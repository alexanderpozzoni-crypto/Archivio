package it.archivio.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

import org.json.*;

import java.util.*;
import okhttp3.*;

public class GmailClient {
    public static final int REQ_SIGN_IN = 9101;
    public static final int REQ_AUTH = 9102;
    public static final String SCOPE_GMAIL = "https://www.googleapis.com/auth/gmail.readonly";

    private final Context context;
    private final OkHttpClient http = new OkHttpClient();

    public GmailClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public Intent signInIntent(Activity a) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(SCOPE_GMAIL))
                .build();
        return GoogleSignIn.getClient(a, gso).getSignInIntent();
    }

    public GoogleSignInAccount parseSignIn(Intent data) throws Exception {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        return task.getResult(Exception.class);
    }

    public GoogleSignInAccount lastAccount() {
        return GoogleSignIn.getLastSignedInAccount(context);
    }

    public String getToken(GoogleSignInAccount account) throws Exception {
        if (account == null || account.getAccount() == null) throw new Exception("Account Google non collegato");
        return GoogleAuthUtil.getToken(context, account.getAccount(), "oauth2:" + SCOPE_GMAIL);
    }

    public ArrayList<MailDoc> fetchNew(String token, long afterMillis) throws Exception {
        ArrayList<MailDoc> out = new ArrayList<>();
        long afterSeconds = Math.max(0, afterMillis / 1000L);
        String q = "after:" + afterSeconds;
        HttpUrl url = HttpUrl.parse("https://gmail.googleapis.com/gmail/v1/users/me/messages")
                .newBuilder().addQueryParameter("q", q).addQueryParameter("maxResults", "100").build();

        Request req = new Request.Builder().url(url).header("Authorization", "Bearer " + token).build();
        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new Exception("Gmail HTTP " + r.code());
            JSONObject root = new JSONObject(r.body().string());
            JSONArray msgs = root.optJSONArray("messages");
            if (msgs == null) return out;
            for (int i=0; i<msgs.length(); i++) {
                String id = msgs.getJSONObject(i).getString("id");
                MailDoc m = fetchMessage(token, id);
                if (m != null) out.add(m);
            }
        }
        return out;
    }

    private MailDoc fetchMessage(String token, String id) throws Exception {
        String url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + id + "?format=full";
        Request req = new Request.Builder().url(url).header("Authorization", "Bearer " + token).build();
        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) return null;
            JSONObject o = new JSONObject(r.body().string());
            MailDoc m = new MailDoc();
            m.id = id;
            m.internalDate = Long.parseLong(o.optString("internalDate","0"));
            JSONObject payload = o.optJSONObject("payload");
            StringBuilder body = new StringBuilder();
            if (payload != null) {
                JSONArray headers = payload.optJSONArray("headers");
                if (headers != null) {
                    for (int i=0;i<headers.length();i++) {
                        JSONObject h=headers.getJSONObject(i);
                        String n=h.optString("name","");
                        String v=h.optString("value","");
                        if ("Subject".equalsIgnoreCase(n)) m.subject=v;
                        else if ("From".equalsIgnoreCase(n)) m.from=v;
                        else if ("Date".equalsIgnoreCase(n)) m.date=v;
                    }
                }
                extractText(payload, body);
            }
            m.text = "Da: "+m.from+"\nData: "+m.date+"\nOggetto: "+m.subject+"\n\n"+body;
            return m;
        }
    }

    private void extractText(JSONObject part, StringBuilder out) throws Exception {
        String mime = part.optString("mimeType","");
        JSONObject body = part.optJSONObject("body");
        if (body != null) {
            String data = body.optString("data","");
            if (!data.isEmpty() && (mime.startsWith("text/plain") || mime.startsWith("text/html"))) {
                byte[] b = Base64.decode(data, Base64.URL_SAFE | Base64.NO_WRAP);
                String s = new String(b, java.nio.charset.StandardCharsets.UTF_8);
                if (mime.startsWith("text/html")) s = s.replaceAll("<[^>]+>"," ");
                out.append(s.replaceAll("\\s+"," ")).append("\n");
            }
        }
        JSONArray parts = part.optJSONArray("parts");
        if (parts != null) for (int i=0;i<parts.length();i++) extractText(parts.getJSONObject(i),out);
    }

    public static class MailDoc {
        public String id="",subject="",from="",date="",text="";
        public long internalDate=0;
    }
}

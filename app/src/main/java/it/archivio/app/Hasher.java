package it.archivio.app;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.security.MessageDigest;

public class Hasher {
    public static String sha256(Context context, Uri uri) {
        try {
            InputStream in;
            if ("file".equals(uri.getScheme())) in = new FileInputStream(new File(uri.getPath()));
            else in = context.getContentResolver().openInputStream(uri);
            if (in == null) return "";
            try (InputStream x = in) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[65536];
                int n;
                while ((n=x.read(buf))>0) md.update(buf,0,n);
                StringBuilder sb=new StringBuilder();
                for(byte b:md.digest()) sb.append(String.format("%02x",b));
                return sb.toString();
            }
        } catch(Exception e){ return ""; }
    }
}

package it.archivio.app;

import android.content.*;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private TextView status,answer;
    private EditText searchBox,question;
    private ProgressBar progress;
    private ListView list;
    private ArrayAdapter<DocRecord> adapter;
    private final ArrayList<DocRecord> shown=new ArrayList<>();
    private final AtomicBoolean stop=new AtomicBoolean(false);
    private final ExecutorService thumbPool=Executors.newFixedThreadPool(2);
    private final LruCache<String,Bitmap> thumbCache=new LruCache<String,Bitmap>(8*1024){
        @Override protected int sizeOf(String key,Bitmap value){return Math.max(1,value.getByteCount()/1024);}
    };
    private ArchiveDb db;
    private TextExtractor extractor;
    private GmailClient gmail;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);setContentView(R.layout.activity_main);
        db=new ArchiveDb(this);extractor=new TextExtractor(this);gmail=new GmailClient(this);
        status=findViewById(R.id.txtStatus);answer=findViewById(R.id.txtAnswer);
        searchBox=findViewById(R.id.editSearch);question=findViewById(R.id.editQuestion);
        progress=findViewById(R.id.progress);list=findViewById(R.id.listFiles);

        adapter=new ArrayAdapter<DocRecord>(this,R.layout.row_document,R.id.txtName,shown){
            @Override public View getView(int pos,View cv,ViewGroup parent){
                View v=cv;
                if(v==null)v=LayoutInflater.from(MainActivity.this).inflate(R.layout.row_document,parent,false);
                TextView a=v.findViewById(R.id.txtName),c=v.findViewById(R.id.txtSnippet);
                ImageView im=v.findViewById(R.id.imgThumb);
                DocRecord d=getItem(pos);
                if(d!=null){
                    a.setText(d.name);c.setText(d.displaySnippet==null?"":d.displaySnippet);
                    bindThumbnail(im,d);
                }
                return v;
            }};
        list.setAdapter(adapter);

        findViewById(R.id.btnPermission).setOnClickListener(v->requestFiles());
        findViewById(R.id.btnGmail).setOnClickListener(v->startActivityForResult(gmail.signInIntent(this),GmailClient.REQ_SIGN_IN));
        findViewById(R.id.btnUpdateAll).setOnClickListener(v->updateAll());
        findViewById(R.id.btnStop).setOnClickListener(v->{stop.set(true);status.setText("Arresto richiesto…");});
        findViewById(R.id.btnSearch).setOnClickListener(v->runSearch(false));
        findViewById(R.id.btnAsk).setOnClickListener(v->runSearch(true));
        list.setOnItemClickListener((p,v,pos,id)->open(shown.get(pos)));
        loadDb();
    }

    private void bindThumbnail(ImageView im,DocRecord d){
        final String key=d.uri==null?"":d.uri;
        im.setTag(key);
        Bitmap cached=thumbCache.get(key);
        if(cached!=null){im.setImageBitmap(cached);return;}
        im.setScaleType(ImageView.ScaleType.CENTER);
        if(d.mime!=null&&d.mime.equals("application/pdf"))im.setImageResource(android.R.drawable.ic_menu_view);
        else if(d.mime!=null&&d.mime.startsWith("image/"))im.setImageResource(android.R.drawable.ic_menu_gallery);
        else if(key.startsWith("gmail:"))im.setImageResource(android.R.drawable.ic_dialog_email);
        else im.setImageResource(android.R.drawable.ic_menu_save);
        if(key.isEmpty()||key.startsWith("gmail:"))return;
        final String mime=d.mime==null?"":d.mime;
        if(!(mime.startsWith("image/")||mime.equals("application/pdf")))return;
        thumbPool.execute(()->{
            Bitmap b=makeThumbnail(key,mime);
            if(b==null)return;
            thumbCache.put(key,b);
            runOnUiThread(()->{
                Object tag=im.getTag();
                if(tag!=null&&key.equals(tag.toString())){im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setImageBitmap(b);}
            });
        });
    }

    private Bitmap makeThumbnail(String path,String mime){
        try{
            if(mime.startsWith("image/")){
                BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);
                int sample=1;while(o.outWidth/sample>320||o.outHeight/sample>320)sample*=2;
                o.inJustDecodeBounds=false;o.inSampleSize=Math.max(1,sample);
                return BitmapFactory.decodeFile(path,o);
            }
            if(mime.equals("application/pdf")){
                File f=new File(path);if(!f.exists())return null;
                try(ParcelFileDescriptor pfd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer r=new PdfRenderer(pfd)){
                    if(r.getPageCount()==0)return null;
                    try(PdfRenderer.Page page=r.openPage(0)){
                        int w=240;int h=Math.max(1,(int)(w*(page.getHeight()/(float)page.getWidth())));
                        h=Math.min(h,320);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                        b.eraseColor(android.graphics.Color.WHITE);page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return b;
                    }
                }
            }
        }catch(Throwable ignored){}
        return null;
    }

    @Override protected void onDestroy(){
        thumbPool.shutdownNow();
        super.onDestroy();
    }

    private boolean filesOk(){return Build.VERSION.SDK_INT<30||Environment.isExternalStorageManager();}
    private void requestFiles(){
        if(Build.VERSION.SDK_INT>=30){
            try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}
            catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}
        }
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(req==GmailClient.REQ_SIGN_IN){
            try{GoogleSignInAccount a=gmail.parseSignIn(data);status.setText("Gmail collegato: "+a.getEmail());}
            catch(Exception e){status.setText("Errore collegamento Gmail");}
        }else if(req==GmailClient.REQ_AUTH){status.setText("Autorizzazione Gmail concessa. Premi AGGIORNA TUTTO.");}
    }

    private void runSearch(boolean smart){
        final String q=(smart?question:searchBox).getText().toString().trim();
        if(q.isEmpty()){answer.setText("Scrivi cosa vuoi cercare.");return;}
        answer.setText(smart?"Approfondisco la pertinenza…":"Ricerca documenti…");
        new Thread(()->{
            try{
                List<DocRecord> hits=smart?SmartSearch.smart(db.all(),q):SmartSearch.normal(db.all(),q);
                ArrayList<DocRecord> results=new ArrayList<>();
                int max=Math.min(smart?30:100,hits.size());
                for(int i=0;i<max;i++)results.add(hits.get(i));
                runOnUiThread(()->{
                    shown.clear();shown.addAll(results);adapter.notifyDataSetChanged();
                    if(results.isEmpty()) answer.setText("Nessun documento pertinente trovato.");
                    else if(smart) answer.setText("Trovati "+hits.size()+" documenti pertinenti. Mostro i "+results.size()+" più rilevanti, ordinati per pertinenza.");
                    else answer.setText("Trovati "+hits.size()+" documenti. Mostro i primi "+results.size()+".");
                });
            }catch(Throwable ex){runOnUiThread(()->answer.setText("Errore ricerca: "+ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage())));}
        }).start();
    }

    private void updateAll(){
        if(!filesOk()){requestFiles();return;}
        stop.set(false);progress.setProgress(0);
        new Thread(()->{
            scanPhone();if(!stop.get())syncGmail();
            runOnUiThread(()->{loadDb();progress.setProgress(100);status.setText(db.count()+" documenti/mail indicizzati • "+db.duplicates()+" duplicati");});
        }).start();
    }

    private void scanPhone(){
        List<File> fs=PhoneScanner.scan(stop);int total=fs.size(),done=0;
        HashSet<String> hashes=new HashSet<>();for(DocRecord d:db.all())if(d.hash!=null&&!d.hash.isEmpty())hashes.add(d.hash);
        for(File f:fs){
            if(stop.get())break;done++;
            String key=f.getAbsolutePath();DocRecord old=db.get(key);
            if(old==null||old.size!=f.length()||old.modified!=f.lastModified()){
                String h=Hasher.sha256(this,Uri.fromFile(f));
                if(old!=null||h.isEmpty()||!hashes.contains(h)){
                    DocRecord d=new DocRecord();d.uri=key;d.name=f.getName();d.mime=mime(f.getName());d.size=f.length();d.modified=f.lastModified();
                    d.hash=h;d.text=extractor.extract(Uri.fromFile(f),d.name,d.mime);d.indexedAt=System.currentTimeMillis();db.upsert(d);if(!h.isEmpty())hashes.add(h);
                }
            }
            final int p=total==0?0:(int)(done*80L/total),fd=done,ft=total;
            runOnUiThread(()->{progress.setProgress(p);status.setText("Telefono "+fd+"/"+ft);});
        }
    }

    private void syncGmail(){
        GoogleSignInAccount a=gmail.lastAccount();if(a==null)return;
        try{
            String token=gmail.getToken(a);
            long last=getPreferences(MODE_PRIVATE).getLong("gmail_last",System.currentTimeMillis()-30L*24*3600*1000);
            ArrayList<GmailClient.MailDoc> mails=gmail.fetchNew(token,last);long newest=last;
            for(GmailClient.MailDoc m:mails){
                DocRecord d=new DocRecord();d.uri="gmail:"+m.id;d.name="EMAIL - "+(m.subject.isEmpty()?"Senza oggetto":m.subject);
                d.mime="message/rfc822";d.size=m.text.length();d.modified=m.internalDate;d.hash=m.id;d.text=m.text;d.indexedAt=System.currentTimeMillis();db.upsert(d);
                if(m.internalDate>newest)newest=m.internalDate;
            }
            getPreferences(MODE_PRIVATE).edit().putLong("gmail_last",newest).apply();
        }catch(UserRecoverableAuthException e){runOnUiThread(()->startActivityForResult(e.getIntent(),GmailClient.REQ_AUTH));}
        catch(Exception e){runOnUiThread(()->status.setText("Gmail: "+e.getMessage()));}
    }

    private void loadDb(){
        shown.clear();shown.addAll(db.all());adapter.notifyDataSetChanged();
        status.setText(db.count()+" documenti/mail indicizzati • "+db.duplicates()+" duplicati • ricerca intelligente pronta");
    }

    private void open(DocRecord d){
        if(d.uri.startsWith("gmail:")){answer.setText(d.text);return;}
        try{File f=new File(d.uri);Uri u=FileProvider.getUriForFile(this,getPackageName()+".files",f);
            Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(u,d.mime==null?"*/*":d.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"Apri documento"));}
        catch(Exception e){Toast.makeText(this,"Impossibile aprire",Toast.LENGTH_LONG).show();}
    }

    private String mime(String n){String x=n.toLowerCase(Locale.ROOT);
        if(x.endsWith(".pdf"))return"application/pdf";if(x.endsWith(".jpg")||x.endsWith(".jpeg"))return"image/jpeg";
        if(x.endsWith(".png"))return"image/png";if(x.endsWith(".webp"))return"image/webp";if(x.endsWith(".zip"))return"application/zip";
        if(x.endsWith(".rtf"))return"application/rtf";if(x.endsWith(".docx"))return"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if(x.endsWith(".xlsx"))return"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";if(x.endsWith(".eml"))return"message/rfc822";return"text/plain";}
}

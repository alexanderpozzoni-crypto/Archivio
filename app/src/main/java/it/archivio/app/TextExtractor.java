package it.archivio.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.zip.*;

public class TextExtractor {
    private final Context context;
    private final TextRecognizer recognizer;

    public TextExtractor(Context context) {
        this.context=context.getApplicationContext();
        PDFBoxResourceLoader.init(this.context);
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public String extract(Uri uri,String name,String mime) {
        String n=name==null?"":name.toLowerCase();
        try{
            if(n.endsWith(".pdf")) return pdf(uri);
            if(n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")||n.endsWith(".webp")) return ocrImage(uri);
            if(n.endsWith(".txt")||n.endsWith(".csv")||n.endsWith(".log")||n.endsWith(".eml")) return text(uri);
            if(n.endsWith(".rtf")) return rtf(readAll(uri,3_000_000));
            if(n.endsWith(".docx")) return docx(readAll(uri,12_000_000));
            if(n.endsWith(".xlsx")) return xlsx(readAll(uri,18_000_000));
            if(n.endsWith(".zip")) return zip(uri);
        }catch(Exception ignored){}
        return "";
    }

    private InputStream open(Uri uri)throws Exception{
        if("file".equals(uri.getScheme()))return new FileInputStream(new File(uri.getPath()));
        return context.getContentResolver().openInputStream(uri);
    }

    private ParcelFileDescriptor pfd(Uri uri)throws Exception{
        if("file".equals(uri.getScheme()))return ParcelFileDescriptor.open(new File(uri.getPath()),ParcelFileDescriptor.MODE_READ_ONLY);
        return context.getContentResolver().openFileDescriptor(uri,"r");
    }

    private String pdf(Uri uri)throws Exception{
        byte[] data=readAll(uri,40_000_000);
        String t="";
        try(PDDocument d=PDDocument.load(data)){t=new PDFTextStripper().getText(d);}catch(Exception ignored){}
        if(t.replaceAll("\\s+","").length()>=80)return cap(t);
        return cap(t+"\n"+ocrPdf(uri));
    }

    private String ocrPdf(Uri uri)throws Exception{
        StringBuilder sb=new StringBuilder();
        try(ParcelFileDescriptor f=pfd(uri); PdfRenderer r=new PdfRenderer(f)){
            for(int i=0;i<r.getPageCount();i++){
                try(PdfRenderer.Page p=r.openPage(i)){
                    float sc=Math.min(2f,1800f/Math.max(p.getWidth(),p.getHeight()));
                    Bitmap b=Bitmap.createBitmap(Math.max(1,Math.round(p.getWidth()*sc)),
                            Math.max(1,Math.round(p.getHeight()*sc)),Bitmap.Config.ARGB_8888);
                    p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    sb.append(await(recognizer.process(InputImage.fromBitmap(b,0)))).append("\n");
                    b.recycle();
                    if(sb.length()>2_500_000)break;
                }
            }
        }
        return sb.toString();
    }

    private String ocrImage(Uri uri)throws Exception{
        InputImage image;
        if("file".equals(uri.getScheme())) image=InputImage.fromFilePath(context,Uri.fromFile(new File(uri.getPath())));
        else image=InputImage.fromFilePath(context,uri);
        return await(recognizer.process(image));
    }

    private String await(Task<Text> t)throws Exception{
        CountDownLatch l=new CountDownLatch(1); final String[] o={""};
        t.addOnSuccessListener(x->{o[0]=x.getText();l.countDown();}).addOnFailureListener(e->l.countDown());
        l.await(90,TimeUnit.SECONDS); return cap(o[0]);
    }

    private String text(Uri u)throws Exception{return new String(readAll(u,3_000_000),StandardCharsets.UTF_8);}

    private byte[] readAll(Uri u,int max)throws Exception{
        try(InputStream in=open(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)return new byte[0];byte[] buf=new byte[8192];int total=0,n;
            while((n=in.read(buf))>0&&total<max){int use=Math.min(n,max-total);out.write(buf,0,use);total+=use;}
            return out.toByteArray();
        }
    }

    private String rtf(byte[] d){
        String s=new String(d,StandardCharsets.ISO_8859_1);
        return cap(s.replaceAll("\\\\par[d]?","\n").replaceAll("\\\\tab","\t")
                .replaceAll("\\\\'[0-9a-fA-F]{2}"," ").replaceAll("\\\\[a-zA-Z]+-?\\d* ?"," ")
                .replace("{"," ").replace("}"," ").replaceAll("\\s+"," ").trim());
    }

    private String zip(Uri u)throws Exception{
        StringBuilder o=new StringBuilder();
        try(ZipInputStream z=new ZipInputStream(new BufferedInputStream(open(u)))){
            ZipEntry e;
            while((e=z.getNextEntry())!=null){
                if(e.isDirectory())continue;
                String n=e.getName().toLowerCase();
                o.append("\n[ZIP ").append(e.getName()).append("]\n");
                ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int x;
                while((x=z.read(buf))>0&&b.size()<12_000_000)b.write(buf,0,x);
                byte[] d=b.toByteArray();
                try{
                    if(n.endsWith(".txt")||n.endsWith(".csv")||n.endsWith(".log")||n.endsWith(".eml"))o.append(new String(d,StandardCharsets.UTF_8));
                    else if(n.endsWith(".rtf"))o.append(rtf(d));
                    else if(n.endsWith(".docx"))o.append(docx(d));
                    else if(n.endsWith(".xlsx"))o.append(xlsx(d));
                    else if(n.endsWith(".pdf"))try(PDDocument p=PDDocument.load(d)){o.append(new PDFTextStripper().getText(p));}
                }catch(Exception ignored){}
                if(o.length()>3_000_000)break;
            }
        }
        return cap(o.toString());
    }

    private String docx(byte[] d)throws Exception{
        StringBuilder x=new StringBuilder();
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(d))){
            ZipEntry e;while((e=z.getNextEntry())!=null)if("word/document.xml".equals(e.getName())){
                ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                while((n=z.read(buf))>0&&b.size()<3_000_000)b.write(buf,0,n);
                x.append(new String(b.toByteArray(),StandardCharsets.UTF_8));break;
            }
        }
        return cap(x.toString().replaceAll("</w:p>","\n").replaceAll("<[^>]+>"," ")
                .replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replaceAll("\\s+"," "));
    }

    private String xlsx(byte[] d)throws Exception{
        StringBuilder o=new StringBuilder();
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(d))){
            ZipEntry e;while((e=z.getNextEntry())!=null){
                String n=e.getName();
                if(n.equals("xl/sharedStrings.xml")||n.startsWith("xl/worksheets/sheet")){
                    ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int k;
                    while((k=z.read(buf))>0&&b.size()<2_000_000)b.write(buf,0,k);
                    o.append(" ").append(new String(b.toByteArray(),StandardCharsets.UTF_8).replaceAll("<[^>]+>"," "));
                }
                if(o.length()>3_000_000)break;
            }
        }
        return cap(o.toString().replaceAll("\\s+"," "));
    }

    private String cap(String s){return s==null?"":(s.length()>3_000_000?s.substring(0,3_000_000):s);}
}

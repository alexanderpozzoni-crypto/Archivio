package it.archivio.app;

import java.text.Normalizer;
import java.util.*;

public class SmartSearch {
    private static final Map<String,List<String>> SYN = new HashMap<>();
    private static final Set<String> GENERIC = new HashSet<>();
    private static final Set<String> INTENT = new HashSet<>();

    static {
        put("busta", "cedolino","paga","stipendio","retribuzione","competenze","trattenute","netto");
        put("paga", "busta","cedolino","stipendio","retribuzione","competenze","trattenute","netto");
        put("cedolino", "busta","paga","stipendio","retribuzione","competenze","trattenute","netto");
        put("stipendio", "busta","paga","cedolino","retribuzione","competenze","trattenute","netto");
        put("contratto", "assunzione","accordo","rapporto","lettera","livello","ccnl");
        put("fattura", "ricevuta","imponibile","iva","totale","pagamento");
        put("saldo", "pagamento","residuo","bonifico","totale","dovuto");
        put("istologico", "istologia","anatomia patologica","biopsia","istologico");
        put("referto", "esame","diagnosi","visita","medico","ospedale");
        put("risonanza", "rm","rmn","magnetica","risonanza");
        put("inps", "invalidita","verbale","previdenza","commissione");
        put("inail", "infortunio","invalidita","verbale","menomazione");
        put("tari", "rifiuti","tributo","comune","tassa");
        put("pec", "posta certificata","raccomandata","notifica");
        put("email", "mail","posta elettronica","oggetto");

        GENERIC.addAll(Arrays.asList("documento","documenti","file","pdf","mail","email","cerca","trova","mostra","voglio","dove","del","della","dei","degli","di","da","a","il","lo","la","i","gli","le","un","una","per","con","su","e"));
        INTENT.addAll(Arrays.asList("busta","paga","cedolino","stipendio","contratto","fattura","saldo","istologico","referto","risonanza","inps","inail","tari","pec","email"));
    }

    private static void put(String k, String... vals){ SYN.put(k, Arrays.asList(vals)); }

    public static List<DocRecord> normal(List<DocRecord> docs, String query){
        String q=norm(query); if(q.isEmpty()) return Collections.emptyList();
        List<String> terms=coreTerms(q);
        ArrayList<DocRecord> out=new ArrayList<>();
        for(DocRecord d:docs){
            String n=norm(d.name), t=norm(d.text);
            double score=0;
            if(n.contains(q)) score+=250;
            if(t.contains(q)) score+=150;
            for(String term:terms){
                if(n.contains(term)) score+=55;
                score+=Math.min(45,occ(t,term)*5.0);
            }
            if(score>0){d.score=score;d.displaySnippet=snippet(d.text,terms);out.add(d);}
        }
        out.sort((a,b)->Double.compare(b.score,a.score));
        return out;
    }

    public static List<DocRecord> smart(List<DocRecord> docs, String query){
        String q=norm(query); if(q.isEmpty()) return Collections.emptyList();
        List<String> core=coreTerms(q);
        LinkedHashSet<String> expanded=new LinkedHashSet<>(core);
        for(String c:core){
            List<String> syn=SYN.get(c); if(syn!=null) for(String x:syn) expanded.add(norm(x));
        }

        ArrayList<DocRecord> out=new ArrayList<>();
        for(DocRecord d:docs){
            String n=norm(d.name), t=norm(d.text);
            if(t.length()>160000) t=t.substring(0,160000);
            String all=n+" "+t;

            int coreHits=0;
            double score=0;

            // La frase completa è il segnale più forte.
            if(n.contains(q)) score+=1200;
            if(t.contains(q)) score+=850;

            // Le parole scritte dall'utente valgono molto più dei sinonimi.
            for(String c:core){
                boolean inName=n.contains(c), inText=t.contains(c);
                if(inName||inText) coreHits++;
                if(inName) score+=240;
                if(inText) score+=120+Math.min(100,occ(t,c)*10.0);
            }

            // Più parole originali sono presenti, più il documento sale.
            if(core.size()>1){
                double coverage=(double)coreHits/core.size();
                score+=coverage*coverage*700.0;
                if(coreHits==core.size()) score+=900;
                else if(coreHits==core.size()-1) score+=120;
                else score*=0.22; // evita che una sola parola generica domini il ranking
            }

            // Parole vicine tra loro: "tessera sanitaria ... giada" deve battere
            // documenti che contengono solo "tessera" o solo "sanitaria".
            if(core.size()>1){
                int span=minSpan(all,core);
                if(span>=0){
                    if(span<=60) score+=700;
                    else if(span<=180) score+=420;
                    else if(span<=500) score+=180;
                }
            }

            // I sinonimi servono solo come supporto e non possono superare le parole cercate.
            for(String e:expanded){
                if(core.contains(e)) continue;
                if(n.contains(e)) score+=18;
                score+=Math.min(18,occ(t,e)*3.0);
            }

            // Segnali tipici dei cedolini, utili per "busta paga + nome".
            if(containsAny(core,"busta","paga","cedolino","stipendio")){
                int payroll=0;
                for(String x:Arrays.asList("cedolino","retribuzione","competenze","trattenute","netto","imponibile","irpef","inps"))
                    if(n.contains(x)||t.contains(x)) payroll++;
                score+=payroll*55.0;
            }

            // Con tre o più parole, non mostriamo in alto risultati che ne centrano solo una.
            if(core.size()>=3 && coreHits<2) continue;
            if(score>=40){
                d.score=score;
                d.displaySnippet=snippet(d.text,core);
                out.add(d);
            }
        }
        out.sort((a,b)->Double.compare(b.score,a.score));
        return out;
    }

    private static int minSpan(String text,List<String> terms){
        if(text==null||terms.size()<2) return -1;
        ArrayList<Integer> pos=new ArrayList<>();
        for(String term:terms){
            int p=text.indexOf(term);
            if(p<0) return -1;
            pos.add(p);
        }
        int min=Collections.min(pos),max=Collections.max(pos);
        return max-min;
    }

    // Compatibilità con vecchie chiamate.
    public static List<DocRecord> search(List<DocRecord> docs,String query,boolean ai){return ai?smart(docs,query):normal(docs,query);}

    private static boolean containsAny(List<String> xs,String... vals){
        for(String v:vals) if(xs.contains(v)) return true; return false;
    }

    private static List<String> coreTerms(String q){
        ArrayList<String> out=new ArrayList<>();
        for(String x:q.split("\\s+")) if(x.length()>1 && !GENERIC.contains(x) && !out.contains(x)) out.add(x);
        return out;
    }

    private static int occ(String text,String needle){
        if(text==null||needle==null||needle.length()<2) return 0;
        int c=0,i=0; while((i=text.indexOf(needle,i))>=0){c++;i+=needle.length();if(c>=12)break;} return c;
    }

    private static String snippet(String original,List<String> terms){
        if(original==null||original.trim().isEmpty()) return "";
        String normalized=norm(original); int hit=-1;
        for(String x:terms){int p=normalized.indexOf(x);if(p>=0&&(hit<0||p<hit))hit=p;}
        if(hit<0) hit=0;
        int start=Math.max(0,hit-70),end=Math.min(original.length(),hit+190);
        if(start>original.length()) start=0;
        String s=original.substring(start,end).replaceAll("\\s+"," ").trim();
        if(start>0)s="…"+s;if(end<original.length())s=s+"…";return s;
    }

    public static String norm(String s){
        if(s==null)return "";
        String x=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        return x.replaceAll("[^a-z0-9€%]+"," ").trim();
    }
}

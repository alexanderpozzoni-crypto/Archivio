package it.archivio.app;

import java.util.*;

public class LocalAssistant {
    public static String answer(List<DocRecord> docs, String q) {
        List<DocRecord> hits = SmartSearch.search(docs, q, true);
        if (hits.isEmpty()) return "Non ho trovato documenti pertinenti.";

        StringBuilder sb = new StringBuilder();
        sb.append("Ho trovato ").append(hits.size()).append(" risultati. I più pertinenti:\n\n");
        int max = Math.min(3, hits.size());
        for (int i=0;i<max;i++) {
            DocRecord d = hits.get(i);
            sb.append("• ").append(d.name);
            if (d.displaySnippet != null && !d.displaySnippet.isEmpty())
                sb.append("\n  ").append(d.displaySnippet);
            sb.append("\n");
        }
        sb.append("\nTocca uno dei risultati sotto per aprire il documento.");
        return sb.toString();
    }
}

package it.archivio.app;

public class DocRecord {
    public String uri = "";
    public String name = "";
    public String mime = "";
    public long size = 0;
    public long modified = 0;
    public String hash = "";
    public String text = "";
    public long indexedAt = 0;

    public String displaySnippet = "";
    public double score = 0;

    @Override
    public String toString() {
        if (displaySnippet == null || displaySnippet.trim().isEmpty()) return name;
        return name + "\n" + displaySnippet;
    }
}

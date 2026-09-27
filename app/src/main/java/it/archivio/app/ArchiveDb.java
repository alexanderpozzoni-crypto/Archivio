package it.archivio.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class ArchiveDb extends SQLiteOpenHelper {
    private static final String DB = "archivio.db";
    private static final int VERSION = 1;

    public ArchiveDb(Context context) {
        super(context, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE docs (" +
                "uri TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "mime TEXT," +
                "size INTEGER," +
                "modified INTEGER," +
                "hash TEXT," +
                "text_content TEXT," +
                "indexed_at INTEGER)");
        db.execSQL("CREATE INDEX idx_docs_name ON docs(name)");
        db.execSQL("CREATE INDEX idx_docs_hash ON docs(hash)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public DocRecord get(String uri) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT uri,name,mime,size,modified,hash,text_content,indexed_at FROM docs WHERE uri=?",
                new String[]{uri})) {
            if (c.moveToFirst()) return fromCursor(c);
        }
        return null;
    }

    public void upsert(DocRecord d) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR REPLACE INTO docs(uri,name,mime,size,modified,hash,text_content,indexed_at) VALUES(?,?,?,?,?,?,?,?)",
                new Object[]{d.uri,d.name,d.mime,d.size,d.modified,d.hash,d.text,d.indexedAt});
    }

    public List<DocRecord> all() {
        ArrayList<DocRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT uri,name,mime,size,modified,hash,text_content,indexed_at FROM docs ORDER BY name COLLATE NOCASE", null)) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM docs", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int duplicates() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(n-1),0) FROM (SELECT COUNT(*) n FROM docs WHERE hash IS NOT NULL AND hash<>'' GROUP BY hash HAVING COUNT(*)>1)", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void deleteMissing(List<String> uris) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT uri FROM docs", null)) {
                ArrayList<String> toDelete = new ArrayList<>();
                while (c.moveToNext()) {
                    String u = c.getString(0);
                    if (!uris.contains(u)) toDelete.add(u);
                }
                for (String u : toDelete) db.delete("docs", "uri=?", new String[]{u});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private DocRecord fromCursor(Cursor c) {
        DocRecord d = new DocRecord();
        d.uri = c.getString(0);
        d.name = c.getString(1);
        d.mime = c.getString(2);
        d.size = c.getLong(3);
        d.modified = c.getLong(4);
        d.hash = c.getString(5);
        d.text = c.getString(6);
        d.indexedAt = c.getLong(7);
        return d;
    }
}

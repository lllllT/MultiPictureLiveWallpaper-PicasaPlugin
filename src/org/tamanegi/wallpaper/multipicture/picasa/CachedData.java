package org.tamanegi.wallpaper.multipicture.picasa;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Random;

import org.tamanegi.wallpaper.multipicture.picasa.content.Entry;
import org.tamanegi.wallpaper.multipicture.picasa.content.Feed;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.provider.BaseColumns;

public class CachedData
{
    public static class ContentInfo
    {
        public String path;
        public String photo_id;
        public String url;
        public String name;
        public String timestamp;
        public int rotation;
    }

    private static final int FEATURED_CACHE_LIFETIME = (24 * 60 * 60 * 1000);
    private static final int MAX_CACHED_CONTENT = 50;
    private static final int BUFFER_SIZE = 1024 * 8;

    private Connection connection;
    private DataHelper helper;
    private Random random;
    private File base_dir;

    public CachedData(Context context)
    {
        connection = new Connection(context);
        helper = new DataHelper(context);
        random = new Random();

        try {
            Method method = context.getClass().getMethod("getExternalCacheDir");
            base_dir = (File)method.invoke(context);
        }
        catch(Exception e) {
            base_dir = new File(Environment.getExternalStorageDirectory(),
                                "Android" + File.separator +
                                "data" + File.separator +
                                context.getPackageName() + File.separator +
                                "cache");
        }
        base_dir.mkdirs();
    }

    public synchronized void updatePhotoList(
        GenericUrl url, String account_name)
    {
        String location = url.build();
        SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            long last_update =
                getCacheLastUpdate(db, location, account_name, "");
            long cur_time = System.currentTimeMillis();
            if(last_update >= 0 &&
               cur_time >= last_update &&
               cur_time - last_update < FEATURED_CACHE_LIFETIME) {
                return;
            }

            Feed feed = connection.executeGetFeed(url, account_name);
            if(feed != null && feed.entries != null) {
                updatePhotoList(db, location, account_name, feed);
            }

            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
            db.close();
        }
    }

    public synchronized ContentInfo getCachedContent(
        GenericUrl[] urls, String account_name,
        ContentInfo last_content, OrderType order)
    {
        SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            ContentInfo info =
                getCachedContent(db, urls, account_name, last_content, order);
            if(info == null) {
                return null;
            }

            info.path = updateCachedContent(
                db, info.url, account_name, info.timestamp);
            if(info.path == null) {
                return null;
            }

            db.setTransactionSuccessful();
            return info;
        }
        finally {
            db.endTransaction();
            db.close();
        }
    }

    private long getCacheLastUpdate(SQLiteDatabase db,
                                    String location, String account_name,
                                    String timestamp)
    {
        Cursor cur = db.query(
            CacheInfoColumns.TABLE_NAME,
            CacheInfoColumns.ALL_COLUMNS,
            CacheInfoColumns.LOCATION + " = ? AND " +
            CacheInfoColumns.ACCOUNT_NAME + " = ? AND " +
            CacheInfoColumns.TIMESTAMP + " = ?",
            new String[] { location, account_name, timestamp }, // WHERE
            null, null, null);
        try {
            if(cur.moveToFirst()) {
                return cur.getLong(CacheInfoColumns.COL_IDX_LAST_UPDATE);
            }
            else {
                return -1;
            }
        }
        finally {
            cur.close();
        }
    }

    private long updateCacheLastUpdate(SQLiteDatabase db, String type,
                                       String location, String account_name,
                                       String timestamp)
    {
        db.delete(CacheInfoColumns.TABLE_NAME,
                  CacheInfoColumns.LOCATION + " = ? AND " +
                  CacheInfoColumns.ACCOUNT_NAME + " = ?",
                  new String[] { location, account_name });

        long cur_time = System.currentTimeMillis();

        ContentValues vals = new ContentValues();
        vals.put(CacheInfoColumns.TYPE, type);
        vals.put(CacheInfoColumns.LOCATION, location);
        vals.put(CacheInfoColumns.ACCOUNT_NAME, account_name);
        vals.put(CacheInfoColumns.TIMESTAMP, timestamp);
        vals.put(CacheInfoColumns.LAST_UPDATE, cur_time);
        vals.put(CacheInfoColumns.LAST_ACCESS, cur_time);

        return db.insert(CacheInfoColumns.TABLE_NAME, "", vals);
    }

    private void updateCacheLastAccess(SQLiteDatabase db,
                                       String location, String account_name)
    {
        long cur_time = System.currentTimeMillis();

        ContentValues vals = new ContentValues();
        vals.put(CacheInfoColumns.LAST_ACCESS, cur_time);

        db.update(CacheInfoColumns.TABLE_NAME, vals,
                  CacheInfoColumns.LOCATION + " = ? AND " +
                  CacheInfoColumns.ACCOUNT_NAME + " = ?",
                  new String[] { location, account_name });
    }

    private void updatePhotoList(SQLiteDatabase db,
                                 String location, String account_name,
                                 Feed feed)
    {
        db.delete(AlbumColumns.TABLE_NAME,
                  AlbumColumns.LOCATION + " = ? AND " +
                  AlbumColumns.ACCOUNT_NAME + " = ?",
                  new String[] { location, account_name });

        for(Entry entry : feed.entries) {
            ContentValues vals = new ContentValues();
            vals.put(AlbumColumns.LOCATION, location);
            vals.put(AlbumColumns.ACCOUNT_NAME, account_name);
            vals.put(AlbumColumns.PHOTO_ID, entry.id);
            vals.put(AlbumColumns.CONTENT_URL, entry.getContentUrl());
            vals.put(AlbumColumns.CONTENT_NAME, entry.getTitle());
            vals.put(AlbumColumns.TIMESTAMP, parseLong(entry.timestamp));
            vals.put(AlbumColumns.ROTATION, parseInteger(entry.rotation));

            db.insert(AlbumColumns.TABLE_NAME, "", vals);
        }

        updateCacheLastUpdate(db, CacheInfoColumns.CACHE_TYPE_LIST,
                              location, account_name, "");
    }

    private ContentInfo getCachedContent(
        SQLiteDatabase db,
        GenericUrl[] urls, String account_name,
        ContentInfo last_content, OrderType order)
    {
        String[] locations = new String[urls.length];
        for(int i = 0; i < urls.length; i++) {
            locations[i] = urls[i].build();
        }

        String selection = null;
        String selection_arg = null;
        String order_by = null;
        String limit = "1";

        if(order == OrderType.name_asc) {
            if(last_content != null) {
                selection = AlbumColumns.CONTENT_NAME + " >= ?";
                selection_arg = last_content.name;
            }
            order_by = AlbumColumns.CONTENT_NAME + " ASC";
        }
        else if(order == OrderType.name_desc) {
            if(last_content != null) {
                selection = AlbumColumns.CONTENT_NAME + " <= ?";
                selection_arg = last_content.name;
            }
            order_by = AlbumColumns.CONTENT_NAME + " DESC";
        }
        else if(order == OrderType.date_asc) {
            if(last_content != null) {
                selection = AlbumColumns.TIMESTAMP + " >= ?";
                selection_arg = last_content.timestamp;
            }
            order_by = AlbumColumns.TIMESTAMP + " ASC";
        }
        else if(order == OrderType.date_desc) {
            if(last_content != null) {
                selection = AlbumColumns.TIMESTAMP + " <= ?";
                selection_arg = last_content.timestamp;
            }
            order_by = AlbumColumns.TIMESTAMP + " DESC";
        }
        else {
            limit = null;
        }

        StringBuilder location_where = new StringBuilder();
        String[] where_args = new String[locations.length + 1 +
                                         (last_content != null ? 1 : 0) +
                                         (selection != null ? 1 : 0)];
        for(int i = 0; i < locations.length; i++) {
            location_where
                .append(i == 0 ? "( " : " OR ")
                .append(AlbumColumns.LOCATION)
                .append(" = ?");
            where_args[i] = locations[i];
        }
        location_where.append(" )");

        where_args[locations.length + 0] = account_name;
        if(last_content != null) {
            where_args[locations.length + 1] = last_content.photo_id;
        }
        if(selection != null) {
            where_args[locations.length + 2] = selection_arg;
        }

        Cursor cur = db.query(
            AlbumColumns.TABLE_NAME,
            AlbumColumns.ALL_COLUMNS,
            location_where + " AND " +
            AlbumColumns.ACCOUNT_NAME + " = ?" +
            (last_content != null ?
             " AND " + AlbumColumns.PHOTO_ID + " > ?" : "") +
            (selection != null ? " AND " + selection : ""),
            where_args,                         // WHERE
            null, null,
            (order_by != null ? order_by : "") +
            (last_content != null ?
             ", " + AlbumColumns.PHOTO_ID + " ASC" : ""), // ORDER BY
            limit);                                       // LIMIT
        try {
            if(! cur.moveToFirst()) {
                if(last_content != null) {
                    return getCachedContent(urls, account_name, null, order);
                }
                else {
                    return null;
                }
            }

            if(limit == null) {
                cur.moveToPosition(random.nextInt(cur.getCount()));
            }

            ContentInfo info = new ContentInfo();
            info.photo_id = cur.getString(AlbumColumns.COL_IDX_PHOTO_ID);
            info.url = cur.getString(AlbumColumns.COL_IDX_CONTENT_URL);
            info.name = cur.getString(AlbumColumns.COL_IDX_CONTENT_NAME);
            info.timestamp = cur.getString(AlbumColumns.COL_IDX_TIMESTAMP);
            info.rotation = cur.getInt(AlbumColumns.COL_IDX_ROTATION);

            return info;
        }
        finally {
            cur.close();
        }
    }

    private String updateCachedContent(SQLiteDatabase db,
                                       String url, String account_name,
                                       String timestamp)
    {
        // get cached file if exists
        Cursor cur = db.query(
            CacheInfoColumns.TABLE_NAME,
            CacheInfoColumns.ALL_COLUMNS,
            CacheInfoColumns.LOCATION + " = ? AND " +
            CacheInfoColumns.ACCOUNT_NAME + " = ? AND " +
            CacheInfoColumns.TIMESTAMP + " = ?",
            new String[] { url, account_name, timestamp }, // WHERE
            null, null, null);
        try {
            if(cur.moveToFirst()) {
                String id = cur.getString(CacheInfoColumns.COL_IDX_ID);
                File file = new File(base_dir, id);
                if(file.isFile()) {
                    updateCacheLastAccess(db, url, account_name);
                    return file.getAbsolutePath();
                }
            }
        }
        finally {
            cur.close();
        }

        // delete old files
        adjustCachedContentCount(db);

        // regist id for file name
        long id = updateCacheLastUpdate(db, CacheInfoColumns.CACHE_TYPE_CONTENT,
                                        url, account_name, timestamp);
        if(id < 0) {
            return null;
        }

        File file = new File(base_dir, String.valueOf(id));

        // get content
        HttpResponse response =
            connection.executeGet(new GenericUrl(url), account_name);
        if(response == null) {
            return null;
        }

        // write to cache file
        InputStream in = null;
        BufferedOutputStream out = null;
        try {
            try {
                in = response.getContent();
                out = new BufferedOutputStream(new FileOutputStream(file),
                                               BUFFER_SIZE);

                byte[] buf = new byte[BUFFER_SIZE];
                while(true) {
                    int size = in.read(buf);
                    if(size < 0) {
                        break;
                    }

                    out.write(buf, 0, size);
                }
            }
            finally {
                if(in != null) {
                    in.close();
                }
                else {
                    response.ignore();
                }

                if(out != null) {
                    out.close();
                }
            }
        }
        catch(IOException e) {
            e.printStackTrace();
            file.delete();
            return null;
        }

        return file.getAbsolutePath();
    }

    private void adjustCachedContentCount(SQLiteDatabase db)
    {
        Cursor cur = db.query(
            CacheInfoColumns.TABLE_NAME,
            CacheInfoColumns.ALL_COLUMNS,
            CacheInfoColumns.TYPE + " = ?",
            new String[] { CacheInfoColumns.CACHE_TYPE_CONTENT }, // WHERE
            null, null,
            CacheInfoColumns.LAST_ACCESS + " ASC");
        try {
            int cnt = cur.getCount();
            if(cnt < MAX_CACHED_CONTENT) {
                return;
            }

            for(int i = 0; i <= cnt - MAX_CACHED_CONTENT; i++) {
                cur.moveToNext();

                String id = cur.getString(CacheInfoColumns.COL_IDX_ID);
                File file = new File(base_dir, id);
                file.delete();

                db.delete(CacheInfoColumns.TABLE_NAME,
                          CacheInfoColumns._ID + " = ?",
                          new String[] { id });
            }
        }
        finally {
            cur.close();
        }
    }

    private Integer parseInteger(String val)
    {
        try {
            return new Integer(val);
        }
        catch(Exception e) {
            return null;
        }
    }

    private Long parseLong(String val)
    {
        try {
            return new Long(val);
        }
        catch(Exception e) {
            return null;
        }
    }

    private static interface CacheInfoColumns extends BaseColumns
    {
        public static final String TABLE_NAME = "cache_info";

        public static final String TYPE = "type";
        public static final String LOCATION = "location";
        public static final String ACCOUNT_NAME = "account";
        public static final String TIMESTAMP = "timestamp";
        public static final String LAST_UPDATE = "last_update";
        public static final String LAST_ACCESS = "last_access";

        public static final String[] ALL_COLUMNS = {
            _ID, LOCATION, ACCOUNT_NAME, LAST_UPDATE, LAST_ACCESS
        };
        public static final int COL_IDX_ID = 0;
        public static final int COL_IDX_LAST_UPDATE = 3;

        public static final String CACHE_TYPE_LIST = "list";
        public static final String CACHE_TYPE_CONTENT = "content";
    }

    private static interface AlbumColumns extends BaseColumns
    {
        public static final String TABLE_NAME = "album";

        public static final String LOCATION = "location";
        public static final String ACCOUNT_NAME = "account";
        public static final String PHOTO_ID = "photo_id";
        public static final String CONTENT_URL = "content_url";
        public static final String CONTENT_NAME = "content_name";
        public static final String TIMESTAMP = "timestamp";
        public static final String ROTATION = "rotation";

        public static final String[] ALL_COLUMNS = {
            _ID, LOCATION, ACCOUNT_NAME, PHOTO_ID,
            CONTENT_URL, CONTENT_NAME, TIMESTAMP, ROTATION
        };
        public static final int COL_IDX_PHOTO_ID = 3;
        public static final int COL_IDX_CONTENT_URL = 4;
        public static final int COL_IDX_CONTENT_NAME = 5;
        public static final int COL_IDX_TIMESTAMP = 6;
        public static final int COL_IDX_ROTATION = 7;
    }

    private static class DataHelper extends SQLiteOpenHelper
    {
        private static final String DB_NAME = "list.db";
        private static final int DB_VERSION = 1;

        private DataHelper(Context context)
        {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            db.execSQL(
                "CREATE TABLE " + CacheInfoColumns.TABLE_NAME + " (" +
                CacheInfoColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                CacheInfoColumns.TYPE + " TEXT," +
                CacheInfoColumns.LOCATION + " TEXT," +
                CacheInfoColumns.ACCOUNT_NAME + " TEXT," +
                CacheInfoColumns.TIMESTAMP + " TEXT," +
                CacheInfoColumns.LAST_UPDATE + " INTEGER," +
                CacheInfoColumns.LAST_ACCESS + " INTEGER" +
                ");");

            db.execSQL(
                "CREATE TABLE " + AlbumColumns.TABLE_NAME + " (" +
                AlbumColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                AlbumColumns.LOCATION + " TEXT," +
                AlbumColumns.ACCOUNT_NAME + " TEXT," +
                AlbumColumns.PHOTO_ID + " TEXT," +
                AlbumColumns.CONTENT_URL + " TEXT," +
                AlbumColumns.CONTENT_NAME + " TEXT," +
                AlbumColumns.TIMESTAMP + " INTEGER," +
                AlbumColumns.ROTATION + " INTEGER" +
                ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            db.execSQL("DROP TABLE IF EXISTS " + CacheInfoColumns.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + AlbumColumns.TABLE_NAME);
            onCreate(db);
        }
    }
}

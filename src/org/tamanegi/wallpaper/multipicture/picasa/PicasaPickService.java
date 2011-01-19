package org.tamanegi.wallpaper.multipicture.picasa;

import java.io.File;

import org.tamanegi.wallpaper.multipicture.picasa.content.PicasaUrl;
import org.tamanegi.wallpaper.multipicture.plugin.LazyPickService;
import org.tamanegi.wallpaper.multipicture.plugin.PictureContentInfo;
import org.tamanegi.wallpaper.multipicture.plugin.ScreenInfo;

import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.google.api.client.http.GenericUrl;

public class PicasaPickService extends LazyPickService
{
    private CachedData cached_data;
    private ConnectivityManager conn_mgr;

    @Override
    public void onCreate()
    {
        super.onCreate();

        cached_data = new CachedData(this);
        conn_mgr = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
    }

    @Override
    public LazyPicker onCreateLazyPicker()
    {
        return new PicasaLazyPicker();
    }

    private class PicasaLazyPicker extends LazyPicker
    {
        private SharedPreferences pref;

        private String account_name;
        private OrderType change_order;
        private PicasaUrl[] urls;

        private AsyncUpdateCache task;
        private CachedData.ContentInfo last_content = null;

        @Override
        protected void onStart(String key, ScreenInfo hint)
        {
            pref = PreferenceManager.getDefaultSharedPreferences(
                PicasaPickService.this);

            String mode_key = String.format(Settings.MODE_KEY, key);
            String mode_val =
                pref.getString(mode_key, Settings.MODE_FEATURED_VAL);

            if(Settings.MODE_ALBUM_VAL.equals(mode_val)) {
                // album mode
                String account_key = String.format(Settings.ACCOUNT_KEY, key);
                account_name = pref.getString(account_key, "");

                String userid_key = String.format(Settings.ALBUM_USER_KEY, key);
                String userid_val = pref.getString(userid_key, "");

                String albumid_key = String.format(Settings.ALBUM_ID_KEY, key);
                String albumid_val = pref.getString(albumid_key, "");

                String[] album_ids = albumid_val.split(" ");

                urls = new PicasaUrl[album_ids.length];
                for(int i = 0; i < album_ids.length; i++) {
                    urls[i] = PicasaUrl.albumBasedUrl(userid_val, album_ids[i]);
                    urls[i].kind = "photo";
                }

                String order_key = String.format(Settings.ORDER_KEY, key);
                String order_val = pref.getString(order_key, "random");
                try {
                    change_order = OrderType.valueOf(order_val);
                }
                catch(IllegalArgumentException e) {
                    change_order = OrderType.random;
                }
            }
            else {
                // featured mode
                account_name = "";
                change_order = OrderType.random;
                urls = new PicasaUrl[] { PicasaUrl.featuredPhotosUrl() };
            }

            // get album feed
            startTask();
        }

        @Override
        public PictureContentInfo getNext()
        {
            CachedData.ContentInfo content = null;
            try {
                content = task.get();
            }
            catch(Exception e) {
                // ignore
            }

            if(content != null) {
                last_content = content;
            }
            startTask();

            if(content == null) {
                return null;
            }

            File file = new File(content.path);
            PictureContentInfo info =
                new PictureContentInfo(Uri.fromFile(file), content.rotation);
            return info;
        }

        private void startTask()
        {
            task = new AsyncUpdateCache();
            task.execute(this);
        }
    }

    private class AsyncUpdateCache
        extends AsyncTask<PicasaLazyPicker, Void, CachedData.ContentInfo>
    {
        @Override
        protected CachedData.ContentInfo doInBackground(
            PicasaLazyPicker... pickers)
        {
            PicasaLazyPicker picker = pickers[0];
            CachedData.ContentInfo info = null;

            try {
                if(isNetworkAvailable()) {
                    for(GenericUrl url : picker.urls) {
                        cached_data.updatePhotoList(
                            url, picker.account_name, false);
                    }

                    info = cached_data.getCachedContent(
                        picker.urls, picker.account_name,
                        picker.last_content, picker.change_order);
                }
            }
            catch(Exception e) {
                // ignore
                e.printStackTrace();
            }

            return info;
        }
    }

    private boolean isNetworkAvailable()
    {
        if(! conn_mgr.getBackgroundDataSetting()) {
            return false;
        }

        NetworkInfo info = conn_mgr.getActiveNetworkInfo();
        if(info == null || ! info.isConnected()) {
            return false;
        }

        return true;
    }
}

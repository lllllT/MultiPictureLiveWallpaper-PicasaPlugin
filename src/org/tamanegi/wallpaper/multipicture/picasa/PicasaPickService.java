package org.tamanegi.wallpaper.multipicture.picasa;

import java.io.File;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import org.tamanegi.wallpaper.multipicture.picasa.content.PicasaUrl;
import org.tamanegi.wallpaper.multipicture.plugin.LazyPickService;
import org.tamanegi.wallpaper.multipicture.plugin.PictureContentInfo;
import org.tamanegi.wallpaper.multipicture.plugin.ScreenInfo;

import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;

public class PicasaPickService extends LazyPickService
{
    private static final int LAST_URI_CNT_FACTOR = 2;

    private static final int MSG_LOAD = 2;

    private ConnectivityManager conn_mgr;

    private AtomicInteger picker_cnt;
    private LinkedList<String> last_urls;
    private HandlerThread worker_thread;
    private Handler handler;

    @Override
    public void onCreate()
    {
        super.onCreate();

        conn_mgr = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);

        picker_cnt = new AtomicInteger(0);
        last_urls = new LinkedList<String>();

        worker_thread = new HandlerThread(
            "PicasaPickService.worker",
            Process.THREAD_PRIORITY_BACKGROUND);
        worker_thread.start();
        handler = new Handler(worker_thread.getLooper(), new WorkerCallback());
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        worker_thread.quit();
    }

    @Override
    public LazyPicker onCreateLazyPicker()
    {
        return new PicasaLazyPicker();
    }

    private class WorkerCallback implements Handler.Callback
    {
        @Override
        public boolean handleMessage(Message msg)
        {
            switch(msg.what) {
              case MSG_LOAD:
                  {
                      PicasaLazyPicker picker = (PicasaLazyPicker)msg.obj;
                      int pre_loading_cnt;
                      synchronized(picker) {
                          pre_loading_cnt = picker.loading_cnt;
                      }

                      picker.onLoad();

                      synchronized(picker) {
                          picker.loading_cnt -= pre_loading_cnt;
                          picker.notifyAll();
                      }
                  }
                  break;

              default:
                  return false;
            }

            return true;
        }
    }

    private class PicasaLazyPicker extends LazyPicker
    {
        private SharedPreferences pref;
        private CachedData cached_data;
        private OrderType change_order;

        private CachedData.ContentInfo next_content = null;
        private int loading_cnt = 0;

        @Override
        protected void onStart(String key, ScreenInfo hint)
        {
            picker_cnt.incrementAndGet();

            // preference
            pref = PreferenceManager.getDefaultSharedPreferences(
                PicasaPickService.this);

            String mode_key = String.format(Settings.MODE_KEY, key);
            String mode_val =
                pref.getString(mode_key, Settings.MODE_FEATURED_VAL);

            String account_name;
            PicasaUrl[] urls;

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

            cached_data = new CachedData(PicasaPickService.this,
                                         urls, account_name, change_order);

            // get album feed
            startLoading();
        }

        @Override
        public void onStop()
        {
            picker_cnt.decrementAndGet();
        }

        @Override
        public PictureContentInfo getNext()
        {
            CachedData.ContentInfo content = null;
            synchronized(this) {
                while(loading_cnt > 0) {
                    try {
                        wait();
                    }
                    catch(InterruptedException e) {
                        // ignore
                    }
                }

                content = next_content;
                next_content = null;
            }

            startLoading();

            if(content == null) {
                return null;
            }

            File file = new File(content.path);
            PictureContentInfo info =
                new PictureContentInfo(Uri.fromFile(file), content.rotation);
            return info;
        }

        private void startLoading()
        {
            synchronized(this) {
                loading_cnt += 1;
                handler.obtainMessage(MSG_LOAD, this).sendToTarget();
            }
        }

        private void onLoad()
        {
            CachedData.ContentInfo info = null;

            try {
                if(isNetworkAvailable()) {
                    cached_data.updatePhotoList(false);
                    info = cached_data.getCachedContent(false, last_urls);
                }

                if(info == null && change_order == OrderType.random) {
                    info = cached_data.getCachedContent(true, last_urls);
                }
            }
            catch(Exception e) {
                // ignore
                e.printStackTrace();
            }

            if(info != null) {
                addLastUrl(info.url);
            }

            next_content = info;
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

    private void addLastUrl(String url)
    {
        last_urls.addLast(url);
        adjustLastUrl();
    }

    private void adjustLastUrl()
    {
        while(last_urls.size() > picker_cnt.get() * LAST_URI_CNT_FACTOR) {
            last_urls.removeFirst();
        }
    }
}

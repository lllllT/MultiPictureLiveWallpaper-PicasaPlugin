package org.tamanegi.wallpaper.multipicture.picasa;

import org.tamanegi.wallpaper.multipicture.picasa.content.Entry;
import org.tamanegi.wallpaper.multipicture.picasa.content.Feed;
import org.tamanegi.wallpaper.multipicture.picasa.content.PicasaUrl;
import org.tamanegi.wallpaper.multipicture.plugin.PictureSourceContract;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.GoogleTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.xml.atom.AtomParser;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.view.View;

public class PicasaAlbumSource extends PreferenceActivity
{
    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_USER_ID = "userId";

    private String key;
    private boolean need_clear;
    private Account account;
    private String user_id;

    private SharedPreferences pref;
    private String[] album_ids;

    private boolean processing = false;
    private Feed feed = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.button_preference_list);

        Intent intent = getIntent();
        need_clear = intent.getBooleanExtra(
            PictureSourceContract.EXTRA_CLEAR_PREVIOUS, true);
        key = intent.getStringExtra(PictureSourceContract.EXTRA_KEY);
        String account_val = intent.getStringExtra(EXTRA_ACCOUNT);
        user_id = intent.getStringExtra(EXTRA_USER_ID);
        if(key == null || account_val == null || user_id == null) {
            finish();
        }

        account = new Account(account_val, Settings.ACCOUNT_TYPE);

        addPreferencesFromResource(R.xml.album_pref);
        pref = PreferenceManager.getDefaultSharedPreferences(this);

        // albums
        String album_key = String.format(Settings.ALBUM_ID_KEY, key);
        String album_val = (need_clear ? "" : pref.getString(album_key, ""));
        album_ids = album_val.split(" ");

        // order
        String order_key = String.format(Settings.ORDER_KEY, key);
        String order_val = (need_clear ? "" : pref.getString(order_key, ""));
        try {
            OrderType.valueOf(order_val);
        }
        catch(IllegalArgumentException e) {
            order_val = "random";
        }

        ListPreference order = (ListPreference)
            getPreferenceManager().findPreference("order");
        order.setValue(order_val);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if(feed == null && (! processing)) {
            new AsyncHttpGet().execute();
        }
    }

    public void onButtonOk(View v)
    {
        if(applyModeAlbum()) {
            finish();
        }
    }

    public void onButtonCancel(View v)
    {
        setResult(RESULT_CANCELED);
        finish();
    }

    private boolean applyModeAlbum()
    {
        StringBuilder album_ids = new StringBuilder();
        StringBuilder album_names = new StringBuilder();

        int cnt = feed.entries.size();
        PreferenceGroup group = (PreferenceGroup)
            getPreferenceManager().findPreference("album");

        boolean is_checked = false;
        for(int i = 0; i < cnt; i++) {
            Entry entry = feed.entries.get(i);
            CheckBoxPreference check =
                (CheckBoxPreference)group.getPreference(i);

            if(check.isChecked()) {
                album_ids.append(entry.id).append(" ");
                album_names.append(entry.mediaGroup.title).append(", ");
                is_checked = true;
            }
        }
        if(! is_checked) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.pref_myphotos_title)
                .setMessage(R.string.msg_no_album_select_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return false;
        }

        ListPreference order = (ListPreference)
            getPreferenceManager().findPreference("order");
        String order_val = order.getValue();

        // save
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(String.format(Settings.MODE_KEY, key),
                         Settings.MODE_ALBUM_VAL);
        editor.putString(String.format(Settings.ACCOUNT_KEY, key),
                         account.name);
        editor.putString(String.format(Settings.ALBUM_USER_KEY, key),
                         user_id);
        editor.putString(String.format(Settings.ALBUM_ID_KEY, key),
                         album_ids.toString().trim());
        editor.putString(String.format(Settings.ORDER_KEY, key),
                         order_val);
        editor.commit();

        // activity result
        Intent result = new Intent();
        result.putExtra(
            PictureSourceContract.EXTRA_DESCRIPTION,
            getString(R.string.pref_myphotos_desc_base,
                      album_names.substring(0, album_names.length() - 2)));
        result.putExtra(PictureSourceContract.EXTRA_SERVICE_NAME,
                        new ComponentName(this, PickService.class));

        setResult(RESULT_OK, result);
        return true;
    }

    private void updateAlbumList(Feed feed)
    {
        DialogInterface.OnClickListener finishOnClickListener =
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            };

        if(feed == null) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.pref_myphotos_title)
                .setMessage(R.string.msg_fail_get_albums)
                .setPositiveButton(android.R.string.ok, finishOnClickListener)
                .show();
            return;
        }
        if(feed.entries.size() < 1) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.pref_myphotos_title)
                .setMessage(R.string.msg_no_albums)
                .setPositiveButton(android.R.string.ok, finishOnClickListener)
                .show();
            return;
        }

        PreferenceGroup group = (PreferenceGroup)
            getPreferenceManager().findPreference("album");
        group.removeAll();

        for(Entry entry : feed.entries) {
            CheckBoxPreference check = new CheckBoxPreference(this);
            check.setPersistent(false);
            check.setTitle(entry.mediaGroup.title);
            if(entry.mediaGroup.description != null) {
                check.setSummary(entry.mediaGroup.description);
            }
            for(String id : album_ids) {
                if(id.equals(entry.id)) {
                    check.setChecked(true);
                    break;
                }
            }

            group.addPreference(check);
        }

        this.feed = feed;
    }

    private class AsyncHttpGet
        extends AsyncTask<Void, Void, Feed>
        implements DialogInterface.OnCancelListener
    {
        private AccountManager accmgr;
        private ProgressDialog progress;
        private AccountManagerFuture<Bundle> authtoken_future;

        @Override
        protected void onPreExecute()
        {
            processing = true;

            accmgr = AccountManager.get(PicasaAlbumSource.this);

            progress = ProgressDialog.show(
                PicasaAlbumSource.this,
                getString(R.string.pref_myphotos_title),
                getString(R.string.msg_loading),
                true,
                true, this);
        }

        @Override
        protected Feed doInBackground(Void... params)
        {
            return getFeed();
        }

        @Override
        protected void onPostExecute(Feed result)
        {
            progress.dismiss();
            updateAlbumList(result);

            if(result != null) {
                processing = false;
            }
        }

        @Override
        public void onCancel(DialogInterface dialog)
        {
            if(authtoken_future != null) {
                authtoken_future.cancel(true);
            }
            cancel(true);
            finish();
        }

        private Feed getFeed()
        {
            boolean firsttime = true;
            while(true) {
                String authtoken = getAuthToken();
                if(authtoken == null) {
                    return null;
                }

                HttpTransport transport = GoogleTransport.create();
                GoogleHeaders headers = (GoogleHeaders)transport.defaultHeaders;
                headers.setApplicationName("MPLWP-PicasaAlbumSource/0.0.1");
                headers.gdataVersion = "2";
                headers.setGoogleLogin(authtoken);

                AtomParser parser = new AtomParser();
                parser.namespaceDictionary = Feed.NAMESPACES;
                transport.addParser(parser);

                HttpRequest request = transport.buildGetRequest();

                PicasaUrl url = PicasaUrl.userBasedUrl(user_id);
                url.kind = "album";
                request.url = url;

                try {
                    return request.execute().parseAs(Feed.class);
                    // todo: handle "next" link
                }
                catch(HttpResponseException e) {
                    if(e.response.statusCode == 401 ||
                       e.response.statusCode == 403) {
                        if(firsttime) {
                            accmgr.invalidateAuthToken(
                                Settings.ACCOUNT_TYPE, authtoken);

                            firsttime = false;
                            continue;
                        }
                    }

                    e.printStackTrace();
                    return null;
                }
                catch(Exception e) {
                    e.printStackTrace();
                    return null;
                }

                // not reach here
            }
        }

        private String getAuthToken()
        {
            authtoken_future = accmgr.getAuthToken(
                account, Settings.TOKEN_TYPE, null,
                PicasaAlbumSource.this, null, null);

            try {
                Bundle result = authtoken_future.getResult();
                return result.getString(AccountManager.KEY_AUTHTOKEN);
            }
            catch(Exception e) {
                return null;
            }
        }
    }
}

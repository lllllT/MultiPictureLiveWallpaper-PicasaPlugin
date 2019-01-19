package org.tamanegi.wallpaper.multipicture.picasa;

import java.io.IOException;

import org.tamanegi.wallpaper.multipicture.picasa.content.Feed;
import org.tamanegi.wallpaper.multipicture.picasa.content.FeedResponse;

import com.google.api.client.extensions.android2.AndroidHttp;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.xml.atom.AtomParser;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;

public class Connection
{
    private Context context;

    private AccountManager accmgr;
    private AccountManagerFuture<Bundle> auth_future = null;

    private HttpTransport transport;
    private HttpRequestFactory factory;

    public Connection(Context context)
    {
        this.context = context;

        accmgr = AccountManager.get(context);

        transport = AndroidHttp.newCompatibleTransport();
        factory = transport.createRequestFactory();
    }

    public FeedResponse executeGetFeed(GenericUrl url, String account_name,
                                       boolean follow_next, String etag)
    {
        GenericUrl base_url = url;
        FeedResponse result = null;
        while(true) {
            FeedResponse data = null;
            try {
                HttpResponse response = executeGet(url, account_name, etag);
                if(response == null) {
                    return result;
                }

                data = new FeedResponse(
                    response.getStatusCode(),
                    response.parseAs(Feed.class),
                    response.getHeaders().getETag());
            }
            catch(IOException e) {
                e.printStackTrace();
                return result;
            }
            if(data.isNotModified()) {
                return data;
            }

            if(result == null ||
               result.feed == null || result.feed.entries == null) {
                result = data;
            }
            else if(data.feed != null && data.feed.entries != null) {
                result.feed.entries.addAll(data.feed.entries);
            }

            GenericUrl next_url = null;
            if(follow_next && data.feed != null) {
                String next_link = data.feed.getNextLink();
                if(next_link != null) {
                    next_url = new GenericUrl(next_link);
                }
                else if(data.feed.numphotos > 0) {
                    if(data.feed.startIndex + data.feed.itemsPerPage - 1 < data.feed.numphotos) {
                        next_url = base_url.clone();
                        next_url.set("start-index", String.valueOf(data.feed.startIndex + data.feed.itemsPerPage));
                    }
                }
            }
            if(next_url == null) {
                return result;
            }

            url = next_url;
            etag = null;
        }
    }

    public HttpResponse executeGet(GenericUrl url, String account_name,
                                   String etag)
    {
        boolean firsttime = true;
        while(true) {
            String authtoken = null;
            if(account_name != null && account_name.length() > 0) {
                authtoken = getAuthToken(account_name);
                if(authtoken == null) {
                    return null;
                }
            }

            try {
                HttpRequest request = factory.buildGetRequest(url);
                request.setReadTimeout(60 * 1000);

                GoogleHeaders headers = new GoogleHeaders();
                headers.setApplicationName(getUserAgent());
                headers.gdataVersion = "2";
                if(authtoken != null) {
                    headers.setGoogleLogin(authtoken);
                }
                headers.setIfNoneMatch(etag);
                headers.set("deprecation-extension", "true"); // for extension period until 15 March 2019: https://developers.google.com/picasa-web/docs/3.0/deprecation
                request.setHeaders(headers);

                AtomParser parser = new AtomParser(Feed.NAMESPACES);
                request.addParser(parser);

                return request.execute();
            }
            catch(HttpResponseException e) {
                int code = e.getResponse().getStatusCode();
                if(code == 401 || code == 403) {
                    if(firsttime &&
                       account_name != null && account_name.length() > 0) {
                        accmgr.invalidateAuthToken(
                            Settings.ACCOUNT_TYPE, authtoken);

                        firsttime = false;
                        continue;
                    }
                }
                if(code == 304) {               // Not Modified
                    return e.getResponse();
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

    public void cancel(boolean mayInterruptIfRunning)
    {
        if(auth_future != null) {
            auth_future.cancel(mayInterruptIfRunning);
        }
    }

    private String getAuthToken(String account_name)
    {
        Account account = new Account(account_name, Settings.ACCOUNT_TYPE);

        if(context instanceof Activity) {
            auth_future = accmgr.getAuthToken(
                account, Settings.TOKEN_TYPE, null,
                (Activity)context, null, null);
        }
        else {
            auth_future = accmgr.getAuthToken(
                account, Settings.TOKEN_TYPE,
                true, null, null);
        }

        try {
            Bundle result = auth_future.getResult();
            return result.getString(AccountManager.KEY_AUTHTOKEN);
        }
        catch(Exception e) {
            return null;
        }
    }

    private String getUserAgent()
    {
        String pver = "unknown";
        try {
            PackageInfo pinfo = context.getPackageManager().getPackageInfo(
                context.getPackageName(), 0);
            pver = pinfo.versionName;
        }
        catch(NameNotFoundException e) {
            e.printStackTrace();
            // ignore
        }

        return context.getString(R.string.header_app_name, pver);
    }
}

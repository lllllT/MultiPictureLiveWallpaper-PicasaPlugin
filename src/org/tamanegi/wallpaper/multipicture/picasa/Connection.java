package org.tamanegi.wallpaper.multipicture.picasa;

import java.io.IOException;

import org.tamanegi.wallpaper.multipicture.picasa.content.Feed;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.GoogleTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.xml.atom.AtomParser;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

public class Connection
{
    private Context context;

    private AccountManager accmgr;
    private AccountManagerFuture<Bundle> auth_future = null;

    private HttpTransport transport;
    private GoogleHeaders headers;

    public Connection(Context context)
    {
        this.context = context;

        accmgr = AccountManager.get(context);

        transport = GoogleTransport.create();

        headers = (GoogleHeaders)transport.defaultHeaders;
        headers.setApplicationName(context.getString(R.string.header_app_name));
        headers.gdataVersion = "2";

        AtomParser parser = new AtomParser();
        parser.namespaceDictionary = Feed.NAMESPACES;
        transport.addParser(parser);
    }

    public Feed executeGetFeed(GenericUrl url, String account_name)
    {
        Feed result = null;
        while(true) {
            Feed data;
            try {
                HttpResponse response = executeGet(url, account_name);
                data = (response != null ? response.parseAs(Feed.class) : null);
            }
            catch(IOException e) {
                e.printStackTrace();
                data = null;
            }
            if(data == null) {
                break;
            }

            if(result == null || result.entries == null) {
                result = data;
            }
            else if(data.entries != null) {
                result.entries.addAll(data.entries);
            }

            String next_url = data.getNextLink();
            if(next_url == null) {
                break;
            }

            url = new GenericUrl(next_url);
        }

        return result;
    }

    public HttpResponse executeGet(GenericUrl url, String account_name)
    {
        boolean firsttime = true;
        while(true) {
            String authtoken = null;
            if(account_name != null && account_name.length() > 0) {
                authtoken = getAuthToken(account_name);
                if(authtoken == null) {
                    return null;
                }

                headers.setGoogleLogin(authtoken);
            }

            HttpRequest request = transport.buildGetRequest();
            request.url = url;

            try {
                return request.execute();
            }
            catch(HttpResponseException e) {
                if(e.response.statusCode == 401 ||
                   e.response.statusCode == 403) {
                    if(firsttime &&
                       account_name != null && account_name.length() > 0) {
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
}

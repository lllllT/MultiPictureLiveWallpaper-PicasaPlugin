package org.tamanegi.wallpaper.multipicture.picasa;

import org.tamanegi.wallpaper.multipicture.plugin.PictureSourceContract;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.view.View;

public class AccountSelector extends PreferenceActivity
{
    private String key;
    private boolean need_clear;

    private SharedPreferences pref;

    private Account account = null;
    private Account[] accounts = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_preference_list);

        Intent intent = getIntent();
        need_clear = intent.getBooleanExtra(
            PictureSourceContract.EXTRA_CLEAR_PREVIOUS, true);
        key = intent.getStringExtra(PictureSourceContract.EXTRA_KEY);
        if(key == null) {
            finish();
        }

        addPreferencesFromResource(R.xml.account_pref);
        pref = PreferenceManager.getDefaultSharedPreferences(this);

        // account
        String acc_key = String.format(Settings.ACCOUNT_KEY, key);
        String acc_name = (need_clear ? null : pref.getString(acc_key, null));
        if(acc_name != null) {
            account = new Account(acc_name, Settings.ACCOUNT_TYPE);
        }

        // account list
        updateAccountList();
    }

    @Override
    protected void onActivityResult(int reqCode, int resultCode, Intent data)
    {
        if(resultCode == RESULT_CANCELED) {
            return;
        }

        setResult(resultCode, data);
        finish();
    }

    public void onButtonAddAccount(View v)
    {
        AccountManager am = AccountManager.get(this);

        am.addAccount(
            Settings.ACCOUNT_TYPE, Settings.TOKEN_TYPE,
            null, null, this,
            new AccountManagerCallback<Bundle>() {
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        future.getResult();
                    }
                    catch(AuthenticatorException e) {
                        new AlertDialog.Builder(AccountSelector.this)
                            .setTitle(R.string.pref_account_title)
                            .setMessage(R.string.msg_fail_add_account)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                        return;
                    }
                    catch(Exception e) {
                        // ignore
                    }

                    // update list
                    updateAccountList();
                }
            },
            null);
    }

    private void updateAccountList()
    {
        PreferenceGroup group = (PreferenceGroup)
            getPreferenceManager().findPreference("account");
        group.removeAll();

        AccountManager am = AccountManager.get(this);
        accounts = am.getAccountsByType(Settings.ACCOUNT_TYPE);

        for(Account acc : accounts) {
            CheckBoxPreference check = new CheckBoxPreference(this);
            check.setPersistent(false);
            check.setTitle(acc.name);
            check.setWidgetLayoutResource(
                R.layout.preference_widget_radiobutton);
            check.setChecked(acc.equals(account));
            check.setOnPreferenceChangeListener(new OnCheckChanged(acc));

            group.addPreference(check);
        }
    }

    private class OnCheckChanged
        implements Preference.OnPreferenceChangeListener
    {
        private Account acc;

        private OnCheckChanged(Account acc)
        {
            this.acc = acc;
        }

        @Override
        public boolean onPreferenceChange(Preference preference,
                                          Object newValue)
        {
            Intent next =
                new Intent(AccountSelector.this, PicasaAlbumSource.class)
                .replaceExtras(getIntent())
                .putExtra(PicasaAlbumSource.EXTRA_ACCOUNT, acc.name)
                .putExtra(PicasaAlbumSource.EXTRA_USER_ID, "default")
                .putExtra(PictureSourceContract.EXTRA_CLEAR_PREVIOUS,
                          ! ((CheckBoxPreference)preference).isChecked());
            startActivityForResult(next, 0);
            return false;
        }
    }
}

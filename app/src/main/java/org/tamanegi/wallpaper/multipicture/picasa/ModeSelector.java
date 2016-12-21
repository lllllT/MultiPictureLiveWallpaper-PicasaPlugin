package org.tamanegi.wallpaper.multipicture.picasa;

import org.tamanegi.wallpaper.multipicture.plugin.PictureSourceContract;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class ModeSelector extends PreferenceActivity
{
    private static final String STATE_KEY_SEARCH_TEXT = "search_text";

    private String key;
    private boolean need_clear;

    private SharedPreferences pref;

    private AlertDialog edit_dialog;
    private CharSequence edit_word_text;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        need_clear = intent.getBooleanExtra(
            PictureSourceContract.EXTRA_CLEAR_PREVIOUS, true);
        key = intent.getStringExtra(PictureSourceContract.EXTRA_KEY);
        if(key == null) {
            finish();
        }

        addPreferencesFromResource(R.xml.mode_pref);
        pref = PreferenceManager.getDefaultSharedPreferences(this);

        // mode
        String mode_key = String.format(Settings.MODE_KEY, key);
        String mode_val = (need_clear ? "" : pref.getString(mode_key, ""));

        // featured mode
        CheckBoxPreference featured = (CheckBoxPreference)
            getPreferenceManager().findPreference("featured");
        featured.setChecked(Settings.MODE_FEATURED_VAL.equals(mode_val));
        featured.setOnPreferenceChangeListener(
            new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference,
                                                  Object newValue) {
                    onModeFeatured(preference);
                    return false;
                }
            });

        // community search mode
        CheckBoxPreference search_community = (CheckBoxPreference)
            getPreferenceManager().findPreference("search.community");
        search_community.setChecked(
            Settings.MODE_SEARCH_COMMUNITY_VAL.equals(mode_val));
        search_community.setOnPreferenceChangeListener(
            new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference,
                                                  Object newValue) {
                    onModeSearchCommunity(preference);
                    return false;
                }
            });

        // my photos mode
        CheckBoxPreference myphotos = (CheckBoxPreference)
            getPreferenceManager().findPreference("myphotos");
        myphotos.setChecked(Settings.MODE_ALBUM_VAL.equals(mode_val));
        myphotos.setOnPreferenceChangeListener(
            new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference,
                                                  Object newValue) {
                    onModeMyPhotos(preference);
                    return false;
                }
            });
    }

    @Override
    protected void onSaveInstanceState (Bundle state)
    {
        super.onSaveInstanceState(state);

        if(edit_dialog != null) {
            EditText edit_word = (EditText)
                edit_dialog.findViewById(R.id.dialog_edittext);
            edit_word_text = edit_word.getText();
            state.putCharSequence(STATE_KEY_SEARCH_TEXT, edit_word_text);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle state)
    {
        super.onRestoreInstanceState(state);

        if(state != null) {
            edit_word_text = state.getCharSequence(STATE_KEY_SEARCH_TEXT);
        }
        else {
            edit_word_text = null;
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if(edit_word_text != null) {
            showSearchCommunity(edit_word_text);
            edit_word_text = null;
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if(edit_dialog != null) {
            edit_dialog.dismiss();
            edit_dialog = null;
        }
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

    public void onModeFeatured(Preference preference)
    {
        applyModeFeatured();
        finish();
    }

    public void onModeSearchCommunity(Preference preference)
    {
        CharSequence text =
            (((CheckBoxPreference)preference).isChecked() ?
             pref.getString(String.format(Settings.SEARCH_WORD_KEY, key), "") :
             "");
        showSearchCommunity(text);
    }

    public void onModeMyPhotos(Preference preference)
    {
        Intent next =
            new Intent(this, AccountSelector.class)
            .replaceExtras(getIntent())
            .putExtra(PictureSourceContract.EXTRA_CLEAR_PREVIOUS,
                      ! ((CheckBoxPreference)preference).isChecked());
        startActivityForResult(next, 0);
    }

    private void showSearchCommunity(CharSequence text)
    {
        View view = getLayoutInflater().inflate(
            R.layout.dialog_edittext, null);
        EditText edit_word = (EditText)view.findViewById(R.id.dialog_edittext);
        edit_word.setText(text);

        edit_dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.dlg_search_title)
            .setView(view)
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int witch) {
                        EditText edit_word = (EditText)
                            edit_dialog.findViewById(R.id.dialog_edittext);
                        CharSequence text = edit_word.getText();
                        if(applyModeSearch(Settings.MODE_SEARCH_COMMUNITY_VAL,
                                           text)) {
                            edit_word = null;
                            dialog.dismiss();
                            finish();
                        }
                    }
                })
            .setNeutralButton(
                R.string.btn_view_on_web,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int witch) {
                        EditText edit_word = (EditText)
                            edit_dialog.findViewById(R.id.dialog_edittext);
                        CharSequence text = edit_word.getText();
                        String uri_str =
                            getString(R.string.search_web_uri) +
                            Uri.encode(text.toString());
                        startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(uri_str)));
                    }
                })
            .setNegativeButton(
                android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int witch) {
                        edit_dialog = null;
                    }
                })
            .setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        edit_dialog = null;
                    }
                })
            .show();
    }

    private void applyModeFeatured()
    {
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(String.format(Settings.MODE_KEY, key),
                         Settings.MODE_FEATURED_VAL);
        editor.commit();

        Intent result = new Intent();
        result.putExtra(PictureSourceContract.EXTRA_DESCRIPTION,
                        getString(R.string.pref_featured_desc));
        result.putExtra(PictureSourceContract.EXTRA_SERVICE_NAME,
                        new ComponentName(this, PicasaPickService.class));

        setResult(RESULT_OK, result);
    }

    private boolean applyModeSearch(String mode, CharSequence text)
    {
        String str = text.toString().trim();
        if(str.length() == 0) {
            Toast.makeText(this, R.string.msg_no_search_word,
                           Toast.LENGTH_LONG)
                .show();
            return false;
        }

        SharedPreferences.Editor editor = pref.edit();
        editor.putString(String.format(Settings.MODE_KEY, key), mode);
        editor.putString(String.format(Settings.SEARCH_WORD_KEY, key), str);
        editor.commit();

        Intent result = new Intent();
        result.putExtra(PictureSourceContract.EXTRA_DESCRIPTION,
                        getString(R.string.pref_search_community_desc, str));
        result.putExtra(PictureSourceContract.EXTRA_SERVICE_NAME,
                        new ComponentName(this, PicasaPickService.class));

        setResult(RESULT_OK, result);
        return true;
    }
}

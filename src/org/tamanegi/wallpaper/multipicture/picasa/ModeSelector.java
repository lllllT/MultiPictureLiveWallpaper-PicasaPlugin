package org.tamanegi.wallpaper.multipicture.picasa;

import org.tamanegi.wallpaper.multipicture.plugin.PictureSourceContract;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class ModeSelector extends PreferenceActivity
{
    private String key;
    private boolean need_clear;

    private SharedPreferences pref;

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

    public void onModeMyPhotos(Preference preference)
    {
        Intent next =
            new Intent(this, AccountSelector.class)
            .replaceExtras(getIntent())
            .putExtra(PictureSourceContract.EXTRA_CLEAR_PREVIOUS,
                      ! ((CheckBoxPreference)preference).isChecked());
        startActivityForResult(next, 0);
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
}

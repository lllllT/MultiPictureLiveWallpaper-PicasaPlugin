package org.tamanegi.wallpaper.multipicture.picasa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

public class LaunchActivity extends Activity
{
    private static final String[] REL_PACKAGES = {
        "org.tamanegi.wallpaper.multipicture",
        "org.tamanegi.wallpaper.multipicture.dnt",
    };

    @Override
    protected void onResume()
    {
        super.onResume();

        PackageManager pm = getPackageManager();

        boolean _rel_package_found = false;
        for(String pkg: REL_PACKAGES) {
            try {
                if(pm.getPackageInfo(pkg, 0) != null) {
                    _rel_package_found = true;
                    break;
                }
            }
            catch(PackageManager.NameNotFoundException e) {
                continue;
            }
        }

        final boolean rel_package_found = _rel_package_found;
        new AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(rel_package_found ?
                        R.string.msg_usage : R.string.msg_no_rel_package)
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if(! rel_package_found) {
                            Intent intent = new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(getString(R.string.lwp_search_uri)));
                            try {
                                startActivity(intent);
                            }
                            catch(ActivityNotFoundException e) {
                                Toast.makeText(LaunchActivity.this,
                                               R.string.market_not_found,
                                               Toast.LENGTH_SHORT)
                                    .show();
                            }
                        }

                        finish();
                    }
                })
            .setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                })
            .show();
    }
}

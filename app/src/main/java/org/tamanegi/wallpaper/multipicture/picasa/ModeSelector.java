package org.tamanegi.wallpaper.multipicture.picasa;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class ModeSelector extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, AccountSelector.class);
        intent.putExtras(getIntent());
        startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int reqCode, int resultCode, Intent data)
    {
        if(resultCode != RESULT_CANCELED) {
            setResult(resultCode, data);
        }

        finish();
    }
}

package org.tamanegi.wallpaper.multipicture.picasa;

import org.tamanegi.wallpaper.multipicture.plugin.LazyPickService;
import org.tamanegi.wallpaper.multipicture.plugin.PictureContentInfo;
import org.tamanegi.wallpaper.multipicture.plugin.ScreenInfo;

public class PickService extends LazyPickService
{
    @Override
    public LazyPicker onCreateLazyPicker()
    {
        return new PicasaLazyPicker();
    }

    private class PicasaLazyPicker extends LazyPicker
    {
        protected void onStart(String key, ScreenInfo hint)
        {
        }

        protected void onStop()
        {
        }

        public PictureContentInfo getNext()
        {
            return null;
        }
    }
}

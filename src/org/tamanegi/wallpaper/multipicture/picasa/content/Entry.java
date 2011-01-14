package org.tamanegi.wallpaper.multipicture.picasa.content;

import com.google.api.client.util.Key;

public class Entry
{
    @Key("gphoto:id") public String id;
    @Key("gphoto:rotation") public String rotation;
    @Key("gphoto:timestamp") public String timestamp;
    @Key("media:group") public MediaGroup mediaGroup;
}

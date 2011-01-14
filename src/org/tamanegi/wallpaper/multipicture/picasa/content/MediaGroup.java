package org.tamanegi.wallpaper.multipicture.picasa.content;

import com.google.api.client.util.Key;

public class MediaGroup
{
    @Key("media:title") public String title;
    @Key("media:description") public String description;
    @Key("media:content") public MediaContent content;
}

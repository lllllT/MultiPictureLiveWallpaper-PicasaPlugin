package org.tamanegi.wallpaper.multipicture.picasa.content;

import com.google.api.client.util.Key;

public class MediaContent
{
    @Key("@url") public String url;
    @Key("@type") public String type;
    @Key("@medium") public String medium;
    @Key("@height") public String height;
    @Key("@width") public String width;
}

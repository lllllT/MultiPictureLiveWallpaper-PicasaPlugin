package org.tamanegi.wallpaper.multipicture.picasa.content;

import com.google.api.client.util.Key;

public class Entry
{
    @Key("gphoto:id") public String id;
    @Key("gphoto:rotation") public String rotation;
    @Key("gphoto:timestamp") public String timestamp;
    @Key("media:group") public MediaGroup mediaGroup;

    public String getTitle()
    {
        return (mediaGroup == null ? null : mediaGroup.title);
    }

    public String getDescription()
    {
        return (mediaGroup == null ? null : mediaGroup.description);
    }

    public String getContentUrl()
    {
        return (mediaGroup == null || mediaGroup.content == null ?
                null : mediaGroup.content.url);
    }
}

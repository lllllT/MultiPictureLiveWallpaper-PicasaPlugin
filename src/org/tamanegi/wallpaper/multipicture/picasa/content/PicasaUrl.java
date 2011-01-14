package org.tamanegi.wallpaper.multipicture.picasa.content;

import com.google.api.client.googleapis.GoogleUrl;
import com.google.api.client.util.Key;

public class PicasaUrl extends GoogleUrl
{
    public static final String BASE_URL = "https://picasaweb.google.com/data/";

    public static final int[] VALID_UNCROPPED_SIZES = {
        94, 110, 128, 200, 220, 288, 320, 400, 512, 576,
        640, 720, 800, 912, 1024, 1152, 1280, 1440, 1600 };

    @Key public String access;
    @Key public String bbox;
    @Key public String imgmax;
    @Key public String kind;
    @Key public String l;
    @Key("max-results") public String maxResults;
    @Key public String q;
    @Key("start-index") public String startIndex;
    @Key public String tag;
    @Key public String thumbsize;

    public PicasaUrl(String url)
    {
        super(url);
    }

    public static PicasaUrl relativeUrl(String path)
    {
        return new PicasaUrl(BASE_URL + path);
    }

    public static PicasaUrl userBasedUrl(String userId)
    {
        return relativeUrl("feed/api/user/" + userId);
    }

    public static PicasaUrl contactsBasedUrl(String userId)
    {
        PicasaUrl url = relativeUrl("feed/api/user/" + userId + "/contacts");
        url.kind = "user";
        return url;
    }

    public static PicasaUrl albumBasedUrl(String userId, String albumId)
    {
        return relativeUrl("feed/api/user/" + userId +
                           "/albumid/" + albumId);
    }

    public static PicasaUrl photoBasedUrl(
        String userId, String albumId, String photoId)
    {
        return relativeUrl("feed/api/user/" + userId +
                           "/albumid/" + albumId +
                           "/photoid/" + photoId);
    }

    public static PicasaUrl communitySearchUrl(String searchTerm)
    {
        PicasaUrl url = relativeUrl("feed/api/all");
        url.kind = "photo";
        url.q = searchTerm;
        return url;
    }

    public static PicasaUrl featuredPhotosUrl()
    {
        return relativeUrl("feed/api/featured");
    }
}

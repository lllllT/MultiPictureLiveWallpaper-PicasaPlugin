package org.tamanegi.wallpaper.multipicture.picasa.content;

public class FeedResponse
{
    public int statusCode;
    public Feed feed;
    public String etag;

    public FeedResponse(int status_code, Feed feed, String etag)
    {
        this.statusCode = status_code;
        this.feed = feed;
        this.etag = etag;
    }

    public boolean isNotModified()
    {
        return (statusCode == 304);
    }
}

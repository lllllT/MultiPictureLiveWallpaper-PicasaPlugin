package org.tamanegi.wallpaper.multipicture.picasa.content;

import java.util.List;

import com.google.api.client.util.Key;
import com.google.api.client.xml.XmlNamespaceDictionary;

public class Feed
{
    public static final XmlNamespaceDictionary NAMESPACES =
        new XmlNamespaceDictionary()
        .set("", "http://www.w3.org/2005/Atom")
        .set("openSearch", "http://a9.com/-/spec/opensearch/1.1/")
        .set("media", "http://search.yahoo.com/mrss/")
        .set("georss", "http://www.georss.org/georss")
        .set("gml", "http://www.opengis.net/gml")
        .set("exif", "http://schemas.google.com/photos/exif/2007")
        .set("gphoto", "http://schemas.google.com/photos/2007");

    @Key("link") public List<Link> links;
    @Key("entry") public List<Entry> entries;

    @Key("gphoto:numphotos") public int numphotos;
    @Key("openSearch:startIndex") public int startIndex;
    @Key("openSearch:itemsPerPage") public int itemsPerPage;

    public String getLink(String rel)
    {
        if(links == null) {
            return null;
        }

        for(Link link : links) {
            if(rel.equals(link.rel)) {
                return link.href;
            }
        }

        return null;
    }

    public String getNextLink()
    {
        return getLink("next");
    }
}

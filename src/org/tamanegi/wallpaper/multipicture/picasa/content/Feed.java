package org.tamanegi.wallpaper.multipicture.picasa.content;

import java.util.List;
import java.util.Map;

import com.google.api.client.util.Key;
import com.google.api.client.xml.XmlNamespaceDictionary;

public class Feed
{
    public static final XmlNamespaceDictionary NAMESPACES =
        new XmlNamespaceDictionary();

    static
    {
        Map<String, String> nsmap = NAMESPACES.namespaceAliasToUriMap;
        nsmap.put("", "http://www.w3.org/2005/Atom");
        nsmap.put("openSearch", "http://a9.com/-/spec/opensearchrss/1.0/");
        nsmap.put("media", "http://search.yahoo.com/mrss/");
        nsmap.put("georss", "http://www.georss.org/georss");
        nsmap.put("gml", "http://www.opengis.net/gml");
        nsmap.put("exif", "http://schemas.google.com/photos/exif/2007");
        nsmap.put("gphoto", "http://schemas.google.com/photos/2007");
    }

    @Key("link") public List<Link> links;
    @Key("gphoto:user") public String userId;
    @Key("entry") public List<Entry> entries;

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

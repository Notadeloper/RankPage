package cis5550.test;

import java.net.URISyntaxException;

import cis5550.jobs.CrawlerHelper;

public class URLTesting {
    public static void testNormalizeURL(String referenceURL, String url, String expected) throws URISyntaxException {
        String result = CrawlerHelper.normalizeURL(referenceURL, url).toString();
        
        assert result.equals(expected) : "URL: " + url + "\nExpected: " + expected + "\nResult " + result;
    }
    
    public static void main(String[] args) throws URISyntaxException {
        
        String reference = "https://foo.com:8000/bar/xyz.html";
        
        testNormalizeURL(reference, "#abc", "https://foo.com:8000/bar/xyz.html");
        testNormalizeURL(reference, "#", "https://foo.com:8000/bar/xyz.html");
       
        testNormalizeURL(reference, "blah.html#test", "https://foo.com:8000/bar/blah.html");
        
        testNormalizeURL(reference, "../blubb/123.html", "https://foo.com:8000/blubb/123.html");
        testNormalizeURL(reference, "../blubb/../123.html", "https://foo.com:8000/123.html");
        testNormalizeURL(reference, "../blubb/123.html#", "https://foo.com:8000/blubb/123.html");
        testNormalizeURL(reference, "../blubb/123.html#hi", "https://foo.com:8000/blubb/123.html");
        
        testNormalizeURL(reference, "/one/two.html", "https://foo.com:8000/one/two.html");
        testNormalizeURL(reference, "/", "https://foo.com:8000/");
        testNormalizeURL(reference, "/one/../two.html", "https://foo.com:8000/two.html");
        
        testNormalizeURL(reference, "http://elsewhere.com/some.html", "http://elsewhere.com:80/some.html");
        testNormalizeURL(reference, "elsewhere.com/some.html", "https://foo.com:8000/bar/elsewhere.com/some.html");
        
        testNormalizeURL("https://foo.com/bar/xyz.html#abc", "https://foo.com/bar/xyz.html#abc", "https://foo.com:443/bar/xyz.html");
        
        testNormalizeURL("http://foo.com/", "http://foo.com/", "http://foo.com:80/");
        testNormalizeURL("http://foo.com", "http://foo.com", "http://foo.com:80/");
        
        
        String reference2 = "http://bar.com:80/";
        testNormalizeURL(reference2, "blah.html", "http://bar.com:80/blah.html");
        testNormalizeURL(reference2, "/blah.html", "http://bar.com:80/blah.html");
        
        
        System.out.println("All tests succeeded");
    }
}

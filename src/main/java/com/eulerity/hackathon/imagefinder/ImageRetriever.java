package com.eulerity.hackathon.imagefinder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ImageRetriever {
    private ConcurrentHashMap<String, Boolean> imageList= new ConcurrentHashMap<>();//Stores the images we will display after crawling
    private ConcurrentHashMap<String, Boolean> URLs = new ConcurrentHashMap<>();//hashmap to memoize visited URLs
    private String domain;

    private void retrieveImages(Document doc){
        Elements imageElements = doc.getElementsByTag("img");
        for(Element image: imageElements){
            String imageURL = image.attr("src");
            if(!imageURL.startsWith("/static")){//images that started with /static were broken
                imageList.put(imageURL,true);
            }
        }
    }
    private void mainPageCrawl(String url, CountDownLatch latch){
        System.out.println(Thread.currentThread().getName() + ": Starting crawl of " + url);
        Document doc;
        try {
            doc = Jsoup.connect(url).get();
            populateSubpages(doc);
            retrieveImages(doc);
        } catch (IOException e) {e.printStackTrace();}
        URLs.put(url,true);//update to true because we have just visited this url.
        latch.countDown();
    }
    //formatSubpage filters only for the correct type of subpage, and returns a properly formatted URL
    private String formatSubpage(String subpageURL){
        /*
        changed from .contains to .startsWith to make sure it's only subpages in the same domain
        because subpages that contained the domain string
        anywhere else in the URL would pass the filter of .contains.
         */
        if(subpageURL.startsWith("https://" + domain)) {
            System.out.println(Thread.currentThread().getName() + ": Starting crawl of subPage: " + subpageURL);
            return subpageURL;
        }
        /*
        !contains(.com) works because a regular /instagram.com would pass the startsWith tests below,
        and the formatted url would be https://cnn.com//instagram.com/....
        which is not a valid URL, so an exception is thrown. Now it should be properly filtered.
        */
        else if((subpageURL.startsWith("/")  || subpageURL.startsWith("./") || subpageURL.startsWith("../"))
                && !subpageURL.contains(".com")){
            String url = "https://" + domain + subpageURL;
            System.out.println(Thread.currentThread().getName() + ": Starting crawl of subPage: " + url);
            return url;
        }
        return "";
    }
    private void populateSubpages(Document doc){
        Elements subpages = doc.select("a[href]");//get all links
        for(Element element: subpages){
            String formattedURL = formatSubpage(element.attr("href"));
            if(!formattedURL.equals("")) {
                URLs.put(formattedURL, false);//URL of a proper subpage, false because it hasn't been crawled/visited yet.
            }
        }
    }
    private void subPageCrawl(String subpageURL, CountDownLatch latch){
        try{
            String formattedURL = formatSubpage(subpageURL);
            if(!formattedURL.equals("")){
                Document doc = Jsoup.connect(formattedURL).get();
                retrieveImages(doc); //retrieve the images from the subpage.
            }
        }catch(Exception e){e.printStackTrace();}
        latch.countDown();
    }

    //method called in ImageFinder
    public Set<String> crawlURL(String url){
        try{
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);

            //crawl main page(s)
            CountDownLatch latch = new CountDownLatch(1);//latch to coordinate main thread with the exit of all worker threads for main page urls
            domain = new URI(url).getHost();

            //crawl the main page(s)
            executor.submit(() -> mainPageCrawl(url,latch));
            latch.await();

            //crawl subpages
            CountDownLatch subPageLatch = new CountDownLatch(URLs.size());//URLs contains both main page and subpages
            for(String subpageURL: URLs.keySet()){
                if(URLs.get(subpageURL)){//true if visited
                    System.out.println("We already visited this URL:" + subpageURL);
                    subPageLatch.countDown();
                }
                else{
                    executor.submit(()-> subPageCrawl(subpageURL, subPageLatch));
                }
            }
            subPageLatch.await();

        } catch(Exception e) { e.printStackTrace(); }
        System.out.println("Done.");
        return imageList.keySet();
    }
}
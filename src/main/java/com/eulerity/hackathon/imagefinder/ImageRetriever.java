package com.eulerity.hackathon.imagefinder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.*;

public class ImageRetriever {
    private ConcurrentHashMap<String, Boolean> imageList= new ConcurrentHashMap<>();//Stores the images we will display after crawling
    private ConcurrentHashMap<String, Boolean> visitedURLs = new ConcurrentHashMap<>();//hashmap to memoize visited URLs
    private ConcurrentHashMap<String, Boolean> unvisitedURLs = new ConcurrentHashMap<>();//will contain pages we have yet to visit
    private String domain;
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);

    private void message(String msg, String url){
        System.out.println(Thread.currentThread().getName() + msg + url);
    }
    private String formatURL(String subpageURL){//formatSubpage filters only for the correct type of subpage, and returns a properly formatted URL
        /*
        only subpages in the same domain allowed.
        subpages that contained the domain string
        anywhere else in the URL would pass the filter of .contains. so we use .startsWith
         */
        if(subpageURL.startsWith("https://" + domain)) {
            return subpageURL;
        }
        /*
        !contains(.com) works because a regular /instagram.com would pass the startsWith tests below,
        and the formatted url would be https://cnn.com//instagram.com/....
        which is not a valid URL, so an exception is thrown. Now it should be properly filtered.
        */
        else if((subpageURL.startsWith("/")  || subpageURL.startsWith("./") || subpageURL.startsWith("../"))
                && !subpageURL.contains(".com")){
            return "https://" + domain + subpageURL;
        }
        return "";//in case it's not a subpage, it returns an empty string so the code should check the returning value.
    }
    private void populateSubpages(Document doc){
        Elements subpages = doc.select("a[href]");//get all links
        for(Element element: subpages){
            String formattedURL = formatURL(element.attr("href"));
            if(!formattedURL.equals("")) {
                if(!visitedURLs.containsKey(formattedURL)){
                    unvisitedURLs.put(formattedURL, false);//URL of a proper subpage, false because it hasn't been crawled/visited yet.
                }
            }
        }
    }
    private void retrieveImages(Document doc){
        Elements imageElements = doc.getElementsByTag("img");
        for(Element image: imageElements){
            String imageURL = image.attr("src");
            if(!imageURL.startsWith("/static")){//images that started with /static were broken
                imageList.put(imageURL,true);
            }
        }
    }
    private void pageCrawl(String url, CountDownLatch latch){
        message(": Starting crawl of ", url);
        try {
            String properURL = formatURL(url);
            if (!properURL.equals("")) {
                Document doc = Jsoup.connect(properURL).get();
                retrieveImages(doc);
                populateSubpages(doc);
                visitedURLs.put(properURL,true);
            }
        } catch (IOException e) {e.printStackTrace();}
        latch.countDown();
    }

    //method called in ImageFinder
    public Set<String> crawlURL(String url){
        try{
            domain = new URI(url).getHost();

            //crawl main page(s)
            CountDownLatch latch = new CountDownLatch(1);
            executor.submit(()-> pageCrawl(url, latch));
            latch.await();

            //crawl subpages
            int counter = 0;
            while(true) {
                int subpageLatchSize = Math.min(unvisitedURLs.size(), 250);
                CountDownLatch subPageLatch = new CountDownLatch(subpageLatchSize);
                if(executor.getCompletedTaskCount() + counter > 250){
                    executor.shutdown();
                    break;
                }
                for (String subpageURL : unvisitedURLs.keySet()) {
                    ++counter;
                    if(counter > 250) break;
                    if(visitedURLs.contains(subpageURL)){
                        subPageLatch.countDown();
                    }
                    else if(executor.getCompletedTaskCount() + counter < 250) {
                        executor.submit(() -> pageCrawl(subpageURL, subPageLatch));
                    }
                }
                Thread.sleep(1000);
            }
            System.out.println(executor.awaitTermination(1, TimeUnit.MINUTES));
        } catch(Exception e) { e.printStackTrace(); }

        return imageList.keySet();
    }
}
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
    private final ConcurrentHashMap<String, Boolean> imageList= new ConcurrentHashMap<>();//Stores the images we will display after crawling
    private final ConcurrentHashMap<String, Boolean> visitedURLs = new ConcurrentHashMap<>();//hashmap to memoize visited URLs
    private final ConcurrentLinkedQueue<String> uncrawledURLs = new ConcurrentLinkedQueue<>();
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
                    uncrawledURLs.add(formattedURL);//URL of a proper subpage, false because it hasn't been crawled/visited yet.
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
    private void pageCrawl(String url){
        message(": Starting crawl of ", url);
        try {
            String properURL = formatURL(url);
            if (!properURL.equals("")) {
                visitedURLs.put(properURL,true);
                Document doc = Jsoup.connect(properURL).get();
                retrieveImages(doc);
                populateSubpages(doc);
            }
        } catch (IOException e) {e.printStackTrace();}
    }
    void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
    //method called in ImageFinder
    public Set<String> crawlURL(String url){
        try{
            domain = new URI(url).getHost();

            //crawl main page(s)
            executor.submit(()-> pageCrawl(url));
            while(executor.getCompletedTaskCount() != 1){}

            //crawl subpages

            while(uncrawledURLs.iterator().hasNext() && executor.getCompletedTaskCount() < 150) {
                String subpageURL = uncrawledURLs.poll();
                if(!visitedURLs.containsKey(subpageURL)){
                    executor.submit(() -> pageCrawl(subpageURL));
                }
                Thread.sleep(25);
            }
            System.out.println(uncrawledURLs.size() + " , " + executor.getCompletedTaskCount());
            shutdownAndAwaitTermination(executor);

        } catch(Exception e) { e.printStackTrace(); }
        return imageList.keySet();
    }
}
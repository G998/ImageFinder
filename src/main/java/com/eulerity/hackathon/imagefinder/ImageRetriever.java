package com.eulerity.hackathon.imagefinder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ImageRetriever {
    private ConcurrentHashMap<String, Boolean> imageList= new ConcurrentHashMap<>();//Stores the images we will display after crawling
    private ConcurrentHashMap<String, Boolean> visitedURLs = new ConcurrentHashMap<>();//hashmap to memoize visited URLs

    private void retrieveImages(Document doc){
        Elements imageElements = doc.getElementsByTag("img");
        for(Element image: imageElements){
            String imageURL = image.attr("src");
            if(!imageURL.startsWith("/static")){//images that started with /static were broken
                imageList.put(imageURL,true);
            }
        }
    }
    private void mainPageCrawl(Document doc, String url, CountDownLatch latch){
        System.out.println(Thread.currentThread().getName() + ": Starting crawl of " + url);
        retrieveImages(doc);
        latch.countDown();
    }
    private void subPageCrawl(String subpageURL, List<String> domains, CountDownLatch latch){
        try{
            for(String domain:domains){
                if(subpageURL.contains(domain)){
                    System.out.println(Thread.currentThread().getName() + ": Starting crawl of subPage: " + subpageURL);
                    retrieveImages(Jsoup.connect(subpageURL).get()); //retrieve the images from the subpage.
                }
                else if(subpageURL.startsWith("/") || subpageURL.startsWith("./") || subpageURL.startsWith("../")){
                    String url = "https://" + domain + subpageURL;
                    System.out.println(Thread.currentThread().getName() + ": Starting crawl of subPage: " + url);
                    retrieveImages(Jsoup.connect(url).get());
                }
            }
        }catch(Exception e){e.printStackTrace();}
        latch.countDown();
    }

    //method called in ImageFinder
    public Set<String> crawlURL(String url){
        try{
            imageList.clear();//avoid duplicates on consecutive calls
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);

            //crawl main page(s)
            CountDownLatch latch = new CountDownLatch(1);//latch to coordinate main thread with the exit of all worker threads for main page urls
            ConcurrentHashMap<Element, Boolean> subPages = new ConcurrentHashMap<>();//stores href links of the main page to be used as subpages
            List<String> domains = new ArrayList<>();//stores domains for in-site link checking
            if(visitedURLs.containsKey(url)){
                System.out.println("We already visited this URL.");
            }
            else {
                domains.add(new URI(url).getHost());
                visitedURLs.put(url, true);
                //retrieve a document object with Jsoup
                Document doc = Jsoup.connect(url).get();
                //crawl the main page(s)
                executor.submit(() -> mainPageCrawl(doc, url, latch));
                Elements subpages = doc.select("a[href]");//get all links
                for(Element subpage: subpages){
                    subPages.put(subpage,false);
                }
            }
            latch.await();

            //crawl subpages
            CountDownLatch subPageLatch = new CountDownLatch(subPages.size());
            for(Element page: subPages.keySet()){
                String subpageURL = page.attr("href");
                if(visitedURLs.containsKey(subpageURL)){
                    System.out.println("We already visited this URL:" + subpageURL);
                    subPageLatch.countDown();
                }
                else{
                    visitedURLs.put(subpageURL,true);
                    executor.submit(()-> subPageCrawl(subpageURL,domains, subPageLatch));
                }
            }
            subPageLatch.await();

        } catch(Exception e) { e.printStackTrace(); }
        System.out.println("Done.");
        return imageList.keySet();
    }
}
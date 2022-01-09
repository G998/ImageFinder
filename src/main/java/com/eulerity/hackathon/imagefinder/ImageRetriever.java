package com.eulerity.hackathon.imagefinder;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ImageRetriever extends Thread {
    private CountDownLatch latch;
    private String url;
    private List<String> imageList;

    public ImageRetriever(CountDownLatch latch, List<String> imageList){
        this.latch = latch;
        this.imageList = imageList;
    }
    public void setUrl(String url) {
        this.url = url;
    }

    private void simpleCrawl(){
        try {
            System.out.println("Starting crawl of page: " + url);
            Document doc = Jsoup.connect(url).get();
            Elements imageElements = doc.getElementsByTag("img");
            for(Element image: imageElements){
                imageList.add(image.attr("src"));
            }
            latch.countDown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void run() {
        simpleCrawl();
    }
}

package com.nidhin.url2data;

import java.util.ArrayList;

public class URLData {
    private String title;
    private String html;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    private String text;
    private String url;
    private String boilerText;
    private boolean useBoilerText = false;


    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBoilerText() {
        return boilerText;
    }

    public void setBoilerText(String boilerText) {
        this.boilerText = boilerText;
    }

    public boolean isUseBoilerText() {
        return useBoilerText;
    }

    public void setUseBoilerText(boolean useBoilerText) {
        this.useBoilerText = useBoilerText;
    }

    ArrayList<String> ngrams = new ArrayList<>();
    ArrayList<String> metakeywords = new ArrayList<>();
    String aspiration = null;

    public ArrayList<String> getNgrams() {
        return ngrams;
    }

    public void setNgrams(ArrayList<String> ngrams) {
        this.ngrams = ngrams;
    }

    public ArrayList<String> getMetakeywords() {
        return metakeywords;
    }

    public void setMetakeywords(ArrayList<String> metakeywords) {
        this.metakeywords = metakeywords;
    }

    public String getAspiration() {
        return aspiration;
    }

    public void setAspiration(String aspiration) {
        this.aspiration = aspiration;
    }
}

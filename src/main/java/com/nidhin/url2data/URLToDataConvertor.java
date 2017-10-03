package com.nidhin.url2data;



import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nidhin.url2data.objpool.PoolManager;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import de.l3s.boilerpipe.extractors.DefaultExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.IOUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class URLToDataConvertor {
    static Logger logger = LoggerFactory.getLogger(URLToDataConvertor.class);
    static AtomicInteger custom = new AtomicInteger(0);
    static AtomicInteger boilerDefault = new AtomicInteger(0);
    static AtomicInteger boilerArticle = new AtomicInteger(0);

    public void runExtraction(String[] files){
        ConcurrentHashMap<String, ArrayList<String>> fileURLsMap = new ConcurrentHashMap<>();

        new ArrayList<String>(Arrays.asList(files)).stream()
                .parallel()
                .forEach(s -> {
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(s));
                        String line;
                        fileURLsMap.putIfAbsent(s, new ArrayList<String>());
                        while ((line = br.readLine()) != null){
                            fileURLsMap.get(s).add(line);
                        }
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                });
        ConcurrentHashMap<String, ArrayList<String>> failedHits =new ConcurrentHashMap<>();
        ConcurrentHashMap<String, AtomicInteger> successfullHits = new ConcurrentHashMap<>();
        fileURLsMap.entrySet().stream()
                .parallel()
                .forEach(stringArrayListEntry -> {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    File dir = new File("data/" + stringArrayListEntry.getKey());
                    if (!dir.isDirectory()){
                        dir.mkdirs();
                    }
                    logger.info("File -" + stringArrayListEntry.getKey() + " urls - " + stringArrayListEntry.getValue().size() );
                    AtomicInteger counter = new AtomicInteger(0);
                    AtomicInteger failed = new AtomicInteger(0);
                    failedHits.put(stringArrayListEntry.getKey(), new ArrayList<>());
                    successfullHits.put(stringArrayListEntry.getKey(), new AtomicInteger(0));

                    stringArrayListEntry.getValue().stream()
                            .parallel()
                            .forEach(s -> {
                              int count =  counter.getAndIncrement();
                              if (count % 25 == 0){
                                  logger.info("Status - " + stringArrayListEntry.getKey() + " count - " + count + " failed - " + failed.get());
                              }
                                    URLData urlData = fetchURLAndParse(s);
                                    if (urlData != null){
                                        String json = gson.toJson(urlData);
                                        try {
                                            Path path = Paths.get(dir.getAbsolutePath(), s.substring(0, Math.min(100, s.length())).replaceAll("/","_"));
                                            FileWriter fileWriter = new FileWriter(path.toFile());
                                            fileWriter.write(json);
                                            fileWriter.close();
                                            successfullHits.get(stringArrayListEntry.getKey()).incrementAndGet();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }else {
                                        failed.incrementAndGet();
                                        failedHits.get(stringArrayListEntry.getKey()).add(s);
                                    }
                            });
                });
        logger.info("\n\n -- Successes -- \n\n");
        successfullHits.forEach((s, atomicInteger) -> {
            logger.info(s + " --> " + atomicInteger.get());
        });

        logger.info("custom - " + custom.get() + " boiler default - " + boilerDefault.get() + " boiler article - " + boilerArticle.get());
        logger.info("\n\n --FAILURES-- \n\n");
        failedHits.forEach((s, strings) -> {
            logger.info(s + "failures - " + strings.size());
            for (String str : strings){
                logger.info(str);
            }
        });

    }
    public static URLData fetchURLAndParse(String urlStr){
        ArticleExtractor articleExtractor = ArticleExtractor.INSTANCE;
        DefaultExtractor defaultExtractor = DefaultExtractor.INSTANCE;
        boolean isDefault = true;
        try {
            if (urlStr.toLowerCase().contains("pdf"))
                return null;
            
            ArticleTextExtractor articleTextExtractor = new ArticleTextExtractor();
            URLData urlData = new URLData();
            Document doc = Jsoup.connect(urlStr)
                    .timeout(5000)
                    .followRedirects(true)
                    .referrer("google.com")
                    .userAgent("Mozilla/5.0 (Windows NT 6.3; rv:36.0) Gecko/20100101 Firefox/36.0")
                    .get();
            urlData.setUrl(urlStr);
            urlData.setHtml(doc.html());
            urlData.setTitle(doc.title());
            String boilerArticleText = articleExtractor.getText(doc.html());
            String boilerDefaultText = defaultExtractor.getText(doc.html());
            if (boilerDefaultText.length() > boilerArticleText.length()){
                urlData.setBoilerText(boilerDefaultText);
            }
            else {
                urlData.setBoilerText(boilerArticleText);
                isDefault = false;
            }
            urlData.setText(articleTextExtractor.getRelevantText(doc));
            if (urlData.getBoilerText().length() > urlData.getText().length()) {
                urlData.setUseBoilerText(true);
                if (isDefault)
                    boilerDefault.incrementAndGet();
                else
                    boilerArticle.incrementAndGet();
            }
            else {
                custom.incrementAndGet();
            }
            return urlData;

        } catch (Exception e) {
//            e.printStackTrace();
//            logger.error(urlStr);
//            logger.error(e.getMessage());
            return null;
        }
    }

    public void runEnrichment() throws IOException {
        File dataDir = new File("data");
        File[] subDirs = dataDir.listFiles();
        ArrayList<File> allDataFiles = new ArrayList<>();
        for (int i=0; i< subDirs.length; i++){
            if (subDirs[i].isDirectory())
            allDataFiles.addAll(Arrays.asList(subDirs[i].listFiles()));
        }
        Gson gson = new GsonBuilder().create();
        logger.info("Files to process - " + allDataFiles.size());
       List<URLData> urlDatas =  allDataFiles.stream()
               .parallel()
               .map(file -> {

                    try {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String line;
                        String json = "";
                        while ((line = br.readLine()) != null){
                            json += line;
                        }
                        URLData urlData = gson.fromJson(json, URLData.class);
                        urlData.setAspiration(file.getParentFile().getName());
                        return urlData;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    return null;
               })
               .filter(Objects::nonNull)
               .collect(Collectors.toList());
        allDataFiles = null;
       logger.info("generated url objs - " + urlDatas.size());
       List<URLData> datasWithKwords;
        AtomicLong keywordsCount = new AtomicLong(0);
        {
            ConcurrentHashMap<String, ArrayList<String>> aspirationUrlsMapConc = new ConcurrentHashMap<>(8);
            ConcurrentHashMap<String, ArrayList<String>> urlKeywordsMapConc = new ConcurrentHashMap<>(urlDatas.size());
            ConcurrentHashMap<String, ArrayList<String>> urlMetaKeywordsMapConc = new ConcurrentHashMap<>(urlDatas.size());
//        ConcurrentHashMap<String, HashSet<String>> aspirationKeywordsMapConc = new ConcurrentHashMap<>();
//        ConcurrentHashMap<String, HashSet<double[]>> aspirationKeyVectorsMapConc = new ConcurrentHashMap<>();

            HashMap<String, ArrayList<String>> aspirationUrlsMap = new HashMap<>(8);
            HashMap<String, ArrayList<String>> urlKeywordsMap = new HashMap<>(urlDatas.size());
            HashMap<String, ArrayList<String>> urlMetaKeywordsMap = new HashMap<>(urlDatas.size());
//        HashMap<String, HashSet<String>> aspirationKeywordsMap = new HashMap<>();
//        HashMap<String, HashSet<double[]>> aspirationKeyVectorsMap = new HashMap<>();

            AtomicInteger kextractedurlsCount = new AtomicInteger(0);
            datasWithKwords = urlDatas.stream()
                    .parallel()
                    .map(URLToDataConvertor::extractNgrams)
                    .map(urlData -> {
                        keywordsCount.addAndGet(urlData.getNgrams().size() + urlData.getMetakeywords().size());
                        if (!aspirationUrlsMapConc.containsKey(urlData.getAspiration())) {
                            aspirationUrlsMapConc.put(urlData.getAspiration(), new ArrayList<>());
                        }
                        aspirationUrlsMapConc.get(urlData.getAspiration()).add(urlData.getUrl());
//                   if (!urlKeywordsMapConc.containsKey(urlData.getUrl())){
                        urlKeywordsMapConc.put(urlData.getUrl(), new ArrayList<>());
//                   }
                        urlKeywordsMapConc.get(urlData.getUrl()).addAll(urlData.getNgrams());
//                   if (!urlMetaKeywordsMapConc.containsKey(urlData.getUrl())){
                        urlMetaKeywordsMapConc.put(urlData.getUrl(), new ArrayList<>());
//                   }
                        urlMetaKeywordsMapConc.get(urlData.getUrl()).addAll(urlData.getMetakeywords());

                        int vcount = kextractedurlsCount.incrementAndGet();
                        if (vcount % 25 == 0) {
                            logger.info("finished kword extracting - " + vcount);
                        }
                        return urlData;
                    })
                    .collect(Collectors.toList());

            aspirationUrlsMap.putAll(aspirationUrlsMapConc);
            urlKeywordsMap.putAll(urlKeywordsMapConc);
            urlMetaKeywordsMap.putAll(urlMetaKeywordsMapConc);

            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("aspirationUrlsMap.ser"));
            oos.writeObject(aspirationUrlsMap);
            oos.flush();
            oos.close();

            oos = new ObjectOutputStream(new FileOutputStream("urlKeywordsMap.ser"));
            oos.writeObject(urlKeywordsMap);
            oos.flush();
            oos.close();
            oos = new ObjectOutputStream(new FileOutputStream("urlMetaKeywordsMap.ser"));
            oos.writeObject(urlMetaKeywordsMap);
            oos.flush();
            oos.close();
            try {
                PoolManager.getInstance().stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.gc();
        AtomicInteger vectorizedurlsCount = new AtomicInteger(0);

        Word2Vecdl4j word2Vecdl4j = new Word2Vecdl4j();
        word2Vecdl4j.loadModel("en", new File("GoogleNews-vectors-negative300.bin"));
        ConcurrentHashMap<String, double[]> keyVectorMapConc = new ConcurrentHashMap<>();
        List<HashMap<String, double[]>> mapList = datasWithKwords.stream()
               .map(urlData -> {
                   int vcount = vectorizedurlsCount.incrementAndGet();
                   if (vcount% 25 ==0){
                       logger.info("finished vectorizing - " + (vcount-1));
                   }
                    HashMap<String, double[]> vecMap = new HashMap<>();
                   for (String key : urlData.getNgrams()){
                       String kword = key.toLowerCase().trim();
                       double[] vec = word2Vecdl4j.getVectorForNGram("en", kword);
                       if (vec != null)
                           vecMap.put(kword, vec);

                   }
                   for (String key : urlData.getMetakeywords()){
                       String kword = key.toLowerCase().trim();
                       double[] vec = word2Vecdl4j.getVectorForNGram("en", kword);
                       if (vec != null)
                           vecMap.put(kword, vec);

                   }

                   return vecMap;


               })
                .collect(Collectors.toList());



        HashMap<String, double[]> keyVectorMap = new HashMap<>(keywordsCount.intValue());
        for (HashMap<String, double[]> vecMap : mapList){
            keyVectorMap.putAll(vecMap);
        }
            logger.info("serializing...");

        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("keyVectorMap.ser"));
        oos.writeObject(keyVectorMap);
        oos.flush();
        oos.close();



    }

    public static URLData extractNgrams(URLData urlData){
        PoolManager poolManager = null;
        try {
            poolManager = PoolManager.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        KeywordExtractor keywordExtractor = poolManager.borrowKEObject();
        try {
            try {
                keywordExtractor.loadStopWords("SmartStopListEn");
            } catch (IOException e) {
                e.printStackTrace();
            }
            String html = urlData.getHtml();
            String text = urlData.isUseBoilerText() ? urlData.getBoilerText() : urlData.getText();
            keywordExtractor.setHtmlAndText(html, text);
            try {
                HashSet<String> kwords = keywordExtractor.extractKeyWords();
                urlData.getNgrams().addAll(kwords);
                urlData.getMetakeywords().addAll(keywordExtractor.getStrictMetaKeywords());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (BoilerpipeProcessingException e) {
                e.printStackTrace();
            }
        }catch (Exception e){

        }
        poolManager.returnKEObject(keywordExtractor);
        return urlData;
    }


    public static void main (String args[]) throws IOException {
        URLToDataConvertor urlToDataConvertor = new URLToDataConvertor();
        String[] files ={"Achieve High GPA","Build Community","Discover Purpose","Find Balance","Graduate On Time",
                "Maximize Money","Network Effectively","Pursue Career"};
//        urlToDataConvertor.runExtraction(files);
        urlToDataConvertor.runEnrichment();
//        URLData urlData = fetchURLAndParse("https://dzone.com/articles/networking-like-a-pro-tips-to-master-the-conventio");
//        System.out.println(urlData);


    }
}

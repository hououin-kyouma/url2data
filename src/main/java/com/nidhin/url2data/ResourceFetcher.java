package com.nidhin.url2data;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ResourceFetcher {
    OkHttpClient okHttpClient = new OkHttpClient();
    Type type = new TypeToken<HashMap<String, Object>>(){}.getType();
    Gson gson = new Gson();
    public HashSet<String> urls = new HashSet<>();
    public String file = "";
    public boolean runHook = true;

    public HashMap<String, Object> buildRequestAndHitEndpoint(String q, int count, int offset) throws IOException {
//        HttpUrl.Builder builder = HttpUrl.parse("https://api.cognitive.microsoft.com/bing/v5.0/search").newBuilder();
//        builder.addQueryParameter("count", String.valueOf(count));
//        builder.addQueryParameter("offset", String.valueOf(offset));
//        builder.addQueryParameter("q", q);
//        builder.
//
//        String url = builder.build().toString();
        String url = String.format("https://api.cognitive.microsoft.com/bing/v5.0/search?count=%d&offset=%d&q=%s", count, offset, q);
        System.out.println(url);
        Request request = new Request.Builder().url(url)
                .addHeader("Ocp-Apim-Subscription-Key","342ac749a6364fd2bac6b44116c6b8d4")
                .get().build();

        Response response = okHttpClient.newCall(request).execute();
        String respStr = response.body().string();
        return gson.fromJson(respStr, type);

    }

    public void getWebURLS(String q, String file, int total, int maxIter) throws IOException {

        HashSet<String> urls = new HashSet<>();
//        String q= "Network+business+social+relationships+effectively+strategically+professional+student+college";
        int count = 50;
        int offset = 0;
        boolean repeat = true;
        BufferedWriter bw = new BufferedWriter(new FileWriter(file,true));
        int totalRecdCount =0;
        this.file = file;

        while (repeat && urls.size() < total &&(maxIter-- > 0)) {
            System.out.println("next bing req");
            HashMap<String, Object> respMap = buildRequestAndHitEndpoint(q, count, offset);
            System.out.println("bing req done");
            int recdItemsSize = ((ArrayList<LinkedTreeMap>)((LinkedTreeMap)respMap.get("webPages")).get("value")).size();
            System.out.println("retrieved - " + recdItemsSize);

            List<String> trueUrls =  ((ArrayList<LinkedTreeMap>)((LinkedTreeMap)respMap.get("webPages")).get("value"))
                    .stream()
//                    .parallel()
                    .map(linkedTreeMap -> {
                        String bingUrl = (String) linkedTreeMap.get("url");
//                        System.out.println("starting url fetch -" + bingUrl);
//                        String trueUrl = getFinalURL(bingUrl);
//                        return trueUrl;

                        final ExecutorService executor=Executors.newSingleThreadExecutor();
                        Future<String> stringFuture = executor.submit(new URLCallable(bingUrl));
                        String finalURL = null;
                        try {
                            finalURL = stringFuture.get(30, TimeUnit.SECONDS);
//                            System.out.println("Got true url " + bingUrl);
                        }
                        catch (InterruptedException ie) {
  /* Handle the interruption. Or ignore it. */
//                            System.out.println("Failed to get for(interrupted) " + bingUrl);
                        }
                        catch (ExecutionException ee) {
  /* Handle the error. Or ignore it. */
//                            System.out.println("Failed to get for(execution) " + bingUrl);

                        }
                        catch (TimeoutException te) {
  /* Handle the timeout. Or ignore it. */
                            System.out.println("Failed to get for(timeout) " + bingUrl);

                        }
                        if (!executor.isTerminated())
                            executor.shutdownNow();
                        return finalURL;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            urls.addAll(trueUrls);

            System.out.println("Urls got - " + urls.size());
            if (urls.size()> totalRecdCount) {
                totalRecdCount = urls.size();
                offset = totalRecdCount;
            }
            else {
                offset += 10;
            }
//            for (String url : trueUrls){
//                bw.write(url);
//                bw.newLine();
//
//            }
//            bw.flush();
        }
        this.urls = urls;
        for (String url : urls){
            bw.write(url);
            bw.newLine();
        }
        bw.flush();
        bw.close();
        this.runHook = false;

    }
//    public static String getTheFinalURL(String url){
//
//    }

    public static String getFinalURL(String url) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.connect();
            con.getInputStream();

            if (con.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || con.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                String redirectUrl = con.getHeaderField("Location");
                return getFinalURL(redirectUrl);
            }
        } catch (IOException e){
//            e.printStackTrace();
//            System.out.println("Exception - " + e.getMessage());
//            System.out.println(url);
        }
        return url;
    }

    public static void main(String[] args) throws IOException {
        String q= "";
        String file = "";
        int total;
//        q="Network+business+social+relationships+effectively+strategically+professional+student+college";
//        file = "net_effec.txt";
//        total = 350;
//        q="Purpose+direction+soul+path+discover+find+meaning+life+self-discover+passion+student+college+journey+love";
//        file = "disc_purp.txt";
//        total = 300;
//        q="Graduate+college+university+on-time+finish+4-years+early";
//        file = "grad_time.txt";
//        total = 350;
        q = args[0];
        file = args[1];
        total = Integer.parseInt(args[2]);
        int maxiter = Integer.parseInt(args[3]);
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                super.run();
            }
        });
        new ResourceFetcher().getWebURLS(q, file, total, maxiter);
    }

    public class URLCallable implements Callable<String>{
        String url;
        public URLCallable(String url){
            this.url = url;
        }

        @Override
        public String call() throws Exception {
            return getFinalURL(url);
        }
    }

    public class Hook extends Thread{
        @Override
        public void run() {
            super.run();
            if (!runHook)
                return;
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(file,true));
                for (String url : urls){
                    bw.write(url);
                    bw.newLine();
                }
                bw.flush();
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

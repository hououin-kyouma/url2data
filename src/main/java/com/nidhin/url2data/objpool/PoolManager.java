package com.nidhin.url2data.objpool;


import com.nidhin.url2data.KeywordExtractor;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;


public class PoolManager {

    private final static Object lock = new Object();
    private GenericObjectPool<KeywordExtractor> keywordExtractorGenericObjectPool;
      private Logger logger = LoggerFactory.getLogger(PoolManager.class);
    private static PoolManager poolManager;

    private PoolManager() throws Exception {
        KeywordExtractorFactory keywordExtractorFactory = new KeywordExtractorFactory();
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMinIdle(4);
        poolConfig.setMaxTotal(10);
        keywordExtractorGenericObjectPool = new GenericObjectPool<KeywordExtractor>(keywordExtractorFactory, poolConfig);
        keywordExtractorGenericObjectPool.addObject();
        keywordExtractorGenericObjectPool.addObject();
        keywordExtractorGenericObjectPool.addObject();
        keywordExtractorGenericObjectPool.addObject();

//        logger.info("-------Creating Aspiration Cluster Maps-------");
//        AspirationClusterMapFactory aspirationClusterMapFactory = new AspirationClusterMapFactory();
//        GenericObjectPoolConfig poolConfig2 = new GenericObjectPoolConfig();
//        poolConfig1.setMaxTotal(1);
//        poolConfig1.setMinIdle(1);
//        aspirationTopClusterMapPool = new GenericObjectPool<HashMap<String, HashSet<double[]>>>(aspirationClusterMapFactory, poolConfig2);
//        aspirationTopClusterMapPool.addObject();
    }

    public static PoolManager getInstance() throws Exception {
        synchronized (lock){
            if(poolManager==null){
                poolManager = new PoolManager();
            }
        }
        return poolManager;
    }

    public void stop() throws Exception {
        logger.info("-------Closing All Object Pools-------");

        keywordExtractorGenericObjectPool.close();
        keywordExtractorGenericObjectPool = null;
    }



    public KeywordExtractor borrowKEObject() {

        KeywordExtractor mlModel = null;
        try {
            mlModel = keywordExtractorGenericObjectPool.borrowObject();
        } catch (Exception e) {
            logger.error("Error Borrowing Multilayer Network object from pool");
        }
        return mlModel;
    }

    public void returnKEObject(KeywordExtractor mlModel) {
        try {
            keywordExtractorGenericObjectPool.returnObject(mlModel);
        } catch (Exception e) {
            logger.error("Error returning Multilayer Network object to pool");
        }
    }



}

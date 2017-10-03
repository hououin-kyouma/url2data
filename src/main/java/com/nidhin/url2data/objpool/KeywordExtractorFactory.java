package com.nidhin.url2data.objpool;

import com.nidhin.url2data.KeywordExtractor;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

public class KeywordExtractorFactory extends BasePooledObjectFactory<KeywordExtractor> {
    @Override
    public KeywordExtractor create() throws Exception {
        return new KeywordExtractor();
    }

    @Override
    public PooledObject<KeywordExtractor> wrap(KeywordExtractor keywordExtractor) {
        return new DefaultPooledObject<>(keywordExtractor);
    }
}

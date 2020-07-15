/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.cache;

import org.apache.ibatis.cache.decorators.TransactionalCache;

import java.util.HashMap;
import java.util.Map;

/**
 * 在执行查询操作时，查询二级缓存的地点和存储查询数据的地点是不相同的。
 * 查询二级缓存是 查询二级缓存是从PerpetualCache类的HashMap中获取数据的，也就是说二级缓存真正存放到了这个地方
 *
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

    private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

    public void clear(Cache cache) {
        getTransactionalCache(cache).clear();
    }

    /**
     * 查询二级缓存的地方
     *
     * @param cache
     * @param key
     * @return
     */
    public Object getObject(Cache cache, CacheKey key) {
        TransactionalCache transactionalCache = getTransactionalCache(cache);
        return transactionalCache.getObject(key);
    }

    /**
     * 暂时将二级缓存保存到TransactionalCache这个类中的entriesToAddOnCommit这个Map中
     * 一旦事务提交 那么将这个缓存保存到二级缓存中
     *
     * @param cache
     * @param key
     * @param value
     */
    public void putObject(Cache cache, CacheKey key, Object value) {
        TransactionalCache transactionalCache = getTransactionalCache(cache);
        transactionalCache.putObject(key, value);
    }

    public void commit() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.commit();
        }
    }

    public void rollback() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.rollback();
        }
    }

    private TransactionalCache getTransactionalCache(Cache cache) {
        return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
    }

}

/**
 * Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * TransactionalCache实现了Cache接口，
 * 作用是保存某个sqlSession的某个事务中需要向某个二级缓存中添加的缓存数据，
 * 换句话说就是：某些缓存数据会先保存在这里，然后再提交到二级缓存中。
 * <p>
 * TransactionalCacheManager 内部维护了 Cache 实例与 TransactionalCache 实例间的映 射关系，该类也仅负责维护两者的映射关系，
 * 真正做事的还是 TransactionalCache。 TransactionalCache 是一种缓存装饰器，可以为 Cache 实例增加事务功能。脏读问题正是由该类进行处理的。
 * <p>
 * MyBatis 引入事务 缓存解决了脏读问题，事务间只能读取到其他事务提交后的内容，
 * 这相当于事务隔离级别中 的“读已提交(Read Committed)”。但需要注意的时，MyBatis 缓存事务机制只能解决脏读 问题，并不能解决“不可重复读”问题。
 * <p>
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

    private static final Log log = LogFactory.getLog(TransactionalCache.class);

    // 底层封装的二级缓存所对应的Cache对象，用到了装饰器模式
    private final Cache delegate;
    // 该字段为true时，则表示当前TransactionalCache不可查询，且提交事务时，会将底层的Cache清空
    private boolean clearOnCommit;
    // 暂时记录添加都TransactionalCahce中的数据，在事务提交时，会将其中的数据添加到二级缓存中
    // 在事务被􏰀交前，所有从数据库中查询的结果将缓存在此集合中
    private final Map<Object, Object> entriesToAddOnCommit;
    // 在事务被􏰀交前，当缓存未命中时，CacheKey 将会被存储在此集合中
    private final Set<Object> entriesMissedInCache;

    public TransactionalCache(Cache delegate) {
        this.delegate = delegate;
        this.clearOnCommit = false;
        this.entriesToAddOnCommit = new HashMap<>();
        this.entriesMissedInCache = new HashSet<>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    /**
     * 查询 如果clearOnCommit为true 表示当前TransactionalCache不可查询
     * <p>
     * 这里使用了delegate进行查询 装饰器模式
     *
     * @param key The key
     * @return
     */
    @Override
    public Object getObject(Object key) {
        // issue #116
        Object object = delegate.getObject(key);
        if (object == null) {
            entriesMissedInCache.add(key);
        }
        // issue #146
        if (clearOnCommit) {
            return null;
        } else {
            return object;
        }
    }

    /**
     * 将键值对存入到 entriesToAddOnCommit 中，而非 delegate 缓存中
     *
     * @param key    Can be any object but usually it is a {@link CacheKey}
     * @param object
     */
    @Override
    public void putObject(Object key, Object object) {
        // 将键值对存入到 entriesToAddOnCommit 中，而非 delegate 缓存中
        entriesToAddOnCommit.put(key, object);
    }

    @Override
    public Object removeObject(Object key) {
        return null;
    }

    /**
     * 清空事务缓存中的数据 并且将clearOnCommit 设置为true
     */
    @Override
    public void clear() {
        clearOnCommit = true;
        entriesToAddOnCommit.clear();
    }

    /**
     * 提交事务 将缓存在TransactionalCache的entriesToAddOnCommit的数据缓存到二级缓存delegate中去
     * 更新操作中，会将clearOnCommit设置为了true，进入此方法：清除二级缓存中的数据
     */
    public void commit() {
        // 由于在上一步更新操作中，clearOnCommit设置为了true，所以进入此方法：清除二级缓存中的数据
        if (clearOnCommit) {
            delegate.clear();
        }
        // 刷新未缓存的结果到 delegate 缓存中
        flushPendingEntries();
        // 重置 entriesToAddOnCommit 和 entriesMissedInCache
        reset();
    }

    public void rollback() {
        unlockMissedEntries();
        reset();
    }

    private void reset() {
        clearOnCommit = false;
        entriesToAddOnCommit.clear();
        entriesMissedInCache.clear();
    }

    private void flushPendingEntries() {
        for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
            // 将 entriesToAddOnCommit 中的内容转存到 delegate 中
            delegate.putObject(entry.getKey(), entry.getValue());
        }
        for (Object entry : entriesMissedInCache) {
            if (!entriesToAddOnCommit.containsKey(entry)) {
                // 存入空值
                delegate.putObject(entry, null);
            }
        }
    }

    private void unlockMissedEntries() {
        for (Object entry : entriesMissedInCache) {
            try {
                delegate.removeObject(entry);
            } catch (Exception e) {
                log.warn("Unexpected exception while notifiying a rollback to the cache adapter. "
                        + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
            }
        }
    }

}

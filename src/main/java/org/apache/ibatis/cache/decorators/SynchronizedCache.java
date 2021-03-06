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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;

/**
 * 我们知道HashMap是线程不安全的，而我们上述分析的方法也没有看到任何加锁的逻辑，
 * 按理说应该是会有并发的问题，可是，
 * 为什么我们从没听说MyBatis'的二级缓存是线程不安全的？
 * mybatis的二级缓存虽然使用的是PerpetualCache,底层使用HashMap进行维护数据，
 * 但是二级缓存的最外层的包装类是我们的  TransactionalCache，它的里层就是同步缓存SynchronizedCache。
 * 查看这个类，发现我们的get，put等操作都是加锁的。
 *
 * @author Clinton Begin
 */
public class SynchronizedCache implements Cache {

    private final Cache delegate;

    public SynchronizedCache(Cache delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public synchronized int getSize() {
        return delegate.getSize();
    }

    /**
     * 责任链模式 第一个责任链 交给SynchronizedCache 保证了二级缓存是线程安全的
     * 下一个责任链是LoggingCache
     * 多线程环境下 保证线程安全的拿到缓存
     *
     * @param key    Can be any object but usually it is a {@link CacheKey}
     * @param object
     */
    @Override
    public synchronized void putObject(Object key, Object object) {
        delegate.putObject(key, object);
    }

    /**
     * 多线程环境下 保证线程安全的拿到缓存
     *
     * @param key The key
     * @return
     */
    @Override
    public synchronized Object getObject(Object key) {
        return delegate.getObject(key);
    }

    @Override
    public synchronized Object removeObject(Object key) {
        return delegate.removeObject(key);
    }

    @Override
    public synchronized void clear() {
        delegate.clear();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

}

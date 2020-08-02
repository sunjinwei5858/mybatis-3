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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 装饰器模式
 * 清除最少使用的
 * Lru (least recently used) cache decorator.
 * <p>
 * 这里的 LRU 算法基于 LinkedHashMap 覆盖其 removeEldestEntry 方法实现
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

    private final Cache delegate;
    private Map<Object, Object> keyMap;
    private Object eldestKey;

    public LruCache(Cache delegate) {
        this.delegate = delegate;
        setSize(4); // // 设置 map 默认大小
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
     * 使用LinkedHashMap ，这里面accessOrder设置为true 表示排序 每次如果取出key 那么将这个key排在最前面
     * 如果排在最后面 那么肯定就是很少使用了
     * <p>
     * 实现LRU算法的关键点：打开一个开关（accessorOrder），重写一个方法，让其返回true时，移除头节点。
     *
     * @param size
     */
    public void setSize(final int size) {
        keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
            private static final long serialVersionUID = 4267176411845948333L;
            // 覆盖该方法，当每次往该map 中put 时数据时，如该方法返回 True，便移除该map中使用最少的Entry
            // 其参数  eldest 为当前最老的  Entry
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
                boolean tooBig = size() > size;
                if (tooBig) {
                    // //记录当前最老的缓存数据的 Key 值，因为要委托给下一个 Cache 实现删除
                    eldestKey = eldest.getKey();
                }
                return tooBig;
            }
        };
    }

    /**
     * put方法
     *
     * 在put的时候 会进行清除缓存里面最少使用的 也就是进行LRU策略
     *
     * @param key   Can be any object but usually it is a {@link CacheKey}
     * @param value
     */
    @Override
    public void putObject(Object key, Object value) {
        delegate.putObject(key, value);
        cycleKeyList(key);
    }

    /**
     * get方法
     * @param key
     *          The key
     * @return
     */
    @Override
    public Object getObject(Object key) {
        // 刷新key在keyMap中的位置
        keyMap.get(key);
        // 从被装饰类中获取相应缓存项
        return delegate.getObject(key);
    }

    @Override
    public Object removeObject(Object key) {
        // 从被装饰类中移除相应的缓存项
        return delegate.removeObject(key);
    }

    @Override
    public void clear() {
        delegate.clear();
        keyMap.clear();
    }


    /**
     * 看看当前实现是否有 eldestKey, 有的话就调用 removeObject ，将该key从cache中移除
     *
     * @param key
     */
    private void cycleKeyList(Object key) {
        // 存储当前 put 到cache中的 key 值
        keyMap.put(key, key);
        // 从被装饰类中移除相应的缓存项
        if (eldestKey != null) {
            delegate.removeObject(eldestKey);
            eldestKey = null;
        }
    }

}

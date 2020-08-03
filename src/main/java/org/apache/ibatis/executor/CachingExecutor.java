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
package org.apache.ibatis.executor;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.SQLException;
import java.util.List;

/**
 * 实现二级缓存的执行器 CachingExecutor
 * 为了和BaseExecutor基础执行器进行剥离职责
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class CachingExecutor implements Executor {

    private final Executor delegate;
    private final TransactionalCacheManager tcm = new TransactionalCacheManager();

    public CachingExecutor(Executor delegate) {
        this.delegate = delegate;
        delegate.setExecutorWrapper(this);
    }

    @Override
    public Transaction getTransaction() {
        return delegate.getTransaction();
    }

    @Override
    public void close(boolean forceRollback) {
        try {
            // issues #499, #524 and #573
            if (forceRollback) {
                tcm.rollback();
            } else {
                tcm.commit();
            }
        } finally {
            delegate.close(forceRollback);
        }
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    /**
     * 更新操作 需要清空缓存
     *
     * @param ms
     * @param parameterObject
     * @return
     * @throws SQLException
     */
    @Override
    public int update(MappedStatement ms, Object parameterObject) throws SQLException {
        // 进入该方法，可知：清空了TransactionalCache中entriesToAddOnCommit和entriesToRemoveOnCommit的数据，同时clearOnCommit设置为true
        flushCacheIfRequired(ms);
        return delegate.update(ms, parameterObject);
    }

    @Override
    public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
        flushCacheIfRequired(ms);
        return delegate.queryCursor(ms, parameter, rowBounds);
    }

    /**
     * cachingExecutor是为了装饰simpleExecutor
     *
     * @param ms
     * @param parameterObject
     * @param rowBounds
     * @param resultHandler
     * @param <E>
     * @return
     * @throws SQLException
     */
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
        // 获取 BoundSql BoundSql 的获取过程较为复杂
        BoundSql boundSql = ms.getBoundSql(parameterObject);
        // 创建 CacheKey
        CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
        // 调用重载方法
        return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
    }

    /**
     * 进行二级缓存的查询
     * 二级缓存相关逻辑实现 使用装饰者模式嵌入了二级缓存逻辑进去
     * <p>
     * 其余的操作 比如 查询数据库 获取数据库连接 全部都交给BaseExecutor执行器做
     *
     * @param ms
     * @param parameterObject
     * @param rowBounds
     * @param resultHandler
     * @param key
     * @param boundSql
     * @param <E>
     * @return
     * @throws SQLException
     */
    @Override
    // CachingExecutor
    public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
            throws SQLException {
        /**
         * 此处的cache就是当mybatis初始化加载mapper映射文件时，如果配置了<cache/>，就会有该cache对象;
         * 注意二级缓存是从MappedStatement 中获取的，而非由 CachingExecutor 创建。
         * 由于MappedStatement存在于全局配置中，可以被多个 CachingExecutor 获取到，这样就会出现 线程安全问题。
         * 线程安全问题可以通过 SynchronizedCache 装饰类解决，该装饰类会在 Cache 实例构造期间 被添加上
         */
        Cache cache = ms.getCache();
        if (cache != null) {
            /**
             * 是否需要刷新缓存，默认情况下，select不需要刷新缓存，insert,delete,update要刷新缓存
             */
            flushCacheIfRequired(ms);
            if (ms.isUseCache() && resultHandler == null) {
                ensureNoOutParams(ms, boundSql);
                //查询二级缓存，二级缓存是存放在PerpetualCache类中的HashMap中的，使用到了装饰器模式  分析此方法
                @SuppressWarnings("unchecked")
                List<E> list = (List<E>) tcm.getObject(cache, key);
                if (list == null) {
                    //如果二级缓存没命中，则调用装饰器模式的这个方法：这方法中是先查询一级缓存，如果还没命中，则会查询数据库
                    // 此处的delegate为SimpleExecutor
                    list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
                    // 把查询出的数据放到TransactionCache的entriesToAddOnCommit这个HashMap中，
                    // 要注意：只是暂时存放到这里，只有当事务提交后，这里的数据才会真正的放到二级缓存中，后面会介绍这个 分析此方法
                    tcm.putObject(cache, key, list); // issue #578 and #116
                }
                return list;
            }
        }
        //如果不使用二级缓存，则调用BaseExecutor的方法
        return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
    }

    @Override
    public List<BatchResult> flushStatements() throws SQLException {
        return delegate.flushStatements();
    }

    /**
     * 事务进行提交 会将二级缓存进行缓存
     * @param required
     * @throws SQLException
     */
    @Override
    public void commit(boolean required) throws SQLException {
        delegate.commit(required);
        tcm.commit();
    }

    @Override
    public void rollback(boolean required) throws SQLException {
        try {
            delegate.rollback(required);
        } finally {
            if (required) {
                tcm.rollback();
            }
        }
    }

    private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
        if (ms.getStatementType() == StatementType.CALLABLE) {
            for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
                if (parameterMapping.getMode() != ParameterMode.IN) {
                    throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
                }
            }
        }
    }

    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
        return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
    }

    @Override
    public boolean isCached(MappedStatement ms, CacheKey key) {
        return delegate.isCached(ms, key);
    }

    @Override
    public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
        delegate.deferLoad(ms, resultObject, property, key, targetType);
    }

    @Override
    public void clearLocalCache() {
        delegate.clearLocalCache();
    }

    /**
     * 刷新缓存，默认情况下，select不需要刷新缓存，insert,delete,update要刷新缓存。
     * 进行清空TransactionCache类中的entriesToAddOnCommit容器map中的数据
     * 但是二级缓存中的数据对象并未清除【二级缓存中的数据存放在PerpetualCache中】，所以进入第二步事务提交。
     *
     * @param ms
     */
    private void flushCacheIfRequired(MappedStatement ms) {
        Cache cache = ms.getCache();
        if (cache != null && ms.isFlushCacheRequired()) {
            // 清空事务缓存容器中的数据
            tcm.clear(cache);
        }
    }

    @Override
    public void setExecutorWrapper(Executor executor) {
        throw new UnsupportedOperationException("This method should not be called");
    }

}

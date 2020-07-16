package mytest;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.domain.User;
import org.apache.ibatis.executor.*;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapper.UserMapper;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 1。各种执行器实现分析
 * 2。一级缓存逻辑实现分析
 * 3。二级缓存逻辑实现分析
 */
public class MyTest {

    private String resource = "mybatis-config.xml";

    private InputStream inputStream;

    private SqlSessionFactoryBuilder sqlSessionFactoryBuilder;

    private SqlSessionFactory sqlSessionFactory;

    /**
     * @throws IOException
     * @Before 和@After 被 @BeforeEach 和@AfterEach给替代了. 还有一些其他的的注解也被替代了.
     */
    @BeforeEach
    public void init() throws IOException {
        inputStream = Resources.getResourceAsStream(resource);
        /**
         * 建造者模式
         * SqlSessionFactoryBuilder 类相当于一个建造工厂，先读取文件或者配置信息、再解析配置、然后通过反射生成对象，
         * 最后再把结果存入缓存，这样就一步步构建造出一个 SqlSessionFactory 对象。
         */
        sqlSessionFactoryBuilder = new SqlSessionFactoryBuilder();
        sqlSessionFactory = sqlSessionFactoryBuilder.build(inputStream);
    }

    /**
     * 测试是否走了一级缓存
     * <p>
     * 测试是否走了二级缓存 开启二级缓存的配置 有三点config标签设置为true xml中加入cash标签, session必须手动commit才会提交到二级缓存
     */
    @Test
    public void sqlSessionTest() {
        /**
         * 工厂模式生产SqlSession
         */
        SqlSession session = sqlSessionFactory.openSession();
        /**
         * 代理模式执行findUserList()方法
         */
        UserMapper userMapper = session.getMapper(UserMapper.class);

        User user1 = userMapper.findUserById(1);


        session.commit();

        User user2 = userMapper.findUserById(1);

        System.out.println(user2 == user1);

    }

    /**
     * 测试SimpleExecutor执行器 一级缓存逻辑分析
     */
    @Test
    public void simpleExecutorTest() throws SQLException {

        Configuration configuration = sqlSessionFactory.getConfiguration();
        MappedStatement mappedStatement = configuration.getMappedStatement("org.apache.ibatis.mapper.UserMapper.findUserList");

        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/springbootdemo", "root", "root");
        JdbcTransaction jdbcTransaction = new JdbcTransaction(connection);

        SimpleExecutor executor = new SimpleExecutor(configuration, jdbcTransaction);

        executor.doQuery(
                mappedStatement, 1,
                RowBounds.DEFAULT, SimpleExecutor.NO_RESULT_HANDLER, mappedStatement.getBoundSql(1)
        );

        executor.doQuery(
                mappedStatement, 1,
                RowBounds.DEFAULT, SimpleExecutor.NO_RESULT_HANDLER, mappedStatement.getBoundSql(1)
        );


    }

    /**
     * 测试ReuseExecutor执行器 一级缓存逻辑分析
     */
    @Test
    public void reuseExecutorTest() throws SQLException {
        Configuration configuration = sqlSessionFactory.getConfiguration();
        MappedStatement mappedStatement = configuration.getMappedStatement("org.apache.ibatis.mapper.UserMapper.findUserList");

        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/springbootdemo", "root", "root");
        JdbcTransaction jdbcTransaction = new JdbcTransaction(connection);

        ReuseExecutor executor = new ReuseExecutor(configuration, jdbcTransaction);
        // 执行第一次
        executor.doQuery(
                mappedStatement, 1,
                RowBounds.DEFAULT, SimpleExecutor.NO_RESULT_HANDLER, mappedStatement.getBoundSql(1)
        );

        // 执行第二次
        executor.doQuery(
                mappedStatement, 1,
                RowBounds.DEFAULT, SimpleExecutor.NO_RESULT_HANDLER, mappedStatement.getBoundSql(1)
        );

    }

    /**
     * 测试BatchExecutor执行器
     */
    @Test
    public void batchExecutorTest() throws SQLException {

        Configuration configuration = sqlSessionFactory.getConfiguration();
        MappedStatement mappedStatement = configuration.getMappedStatement("org.apache.ibatis.mapper.UserMapper.updateName");

        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/springbootdemo", "root", "root");
        JdbcTransaction jdbcTransaction = new JdbcTransaction(connection);

        BatchExecutor executor = new BatchExecutor(configuration, jdbcTransaction);
        Map paramsMap = new HashMap<String, String>();
        paramsMap.put("aa", 1);
        paramsMap.put("bb", "rrrr");
        // doUpdate 方法相当于设置参数
        executor.doUpdate(mappedStatement, paramsMap);
        // 只有设置doFlushStatements为false才会真正去提交
        // 这个就是批处理刷新功能 发射功能
        executor.doFlushStatements(false);
    }

    /**
     * 测试BaseExecutor执行器 一级缓存逻辑
     * 会走缓存逻辑
     */
    @Test
    public void baseExecutorTest() throws SQLException {

        Configuration configuration = sqlSessionFactory.getConfiguration();
        MappedStatement mappedStatement = configuration.getMappedStatement("org.apache.ibatis.mapper.UserMapper.findUserList");

        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/springbootdemo", "root", "root");
        JdbcTransaction jdbcTransaction = new JdbcTransaction(connection);

        BaseExecutor executor = new SimpleExecutor(configuration, jdbcTransaction);

        // 执行第一次
        executor.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);

        // 执行第二次
        executor.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);

    }

    /**
     * 测试CashingExecutor执行器 二级级缓存逻辑分析
     * 使用了装饰者模式
     * 一级缓存是sqlSession级别的 数据立马能查询到
     * 二级缓存是跨线程级别的调用 需要手动提交才会提交到二级缓存
     * <p>
     * 第二次查询顺序：先走二级缓存，然后走一级缓存。如果二级缓存有 那么就不走一级缓存了。
     * <p>
     * 注意：mybatis开启二级缓存 需要怎么配置
     */
    @Test
    public void cashingExecutorTest() throws SQLException {

        Configuration configuration = sqlSessionFactory.getConfiguration();
        MappedStatement mappedStatement = configuration.getMappedStatement("org.apache.ibatis.mapper.UserMapper.findUserList");
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/springbootdemo", "root", "root");
        JdbcTransaction jdbcTransaction = new JdbcTransaction(connection);

        Executor executor = new ReuseExecutor(configuration, jdbcTransaction);
        // 二级缓存相关逻辑
        Executor cashing = new CachingExecutor(executor);

        boolean useCache = mappedStatement.isUseCache();
        System.out.println("是否开启缓存：" + useCache);

        // 执行第一次
        cashing.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);

        // 必须手动提交 才会提交到二级缓存中去
        cashing.commit(true);

        // 执行第二次
        cashing.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
        cashing.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
        cashing.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
        cashing.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
        cashing.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
        cashing.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
        cashing.query(mappedStatement, 10, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);

    }

    /**
     * mybatis的缓存责任链模式
     */
    @Test
    public void cashTest() {
        // 基于类名称 获取缓存
        Configuration configuration = sqlSessionFactory.getConfiguration();

        Cache cache = configuration.getCache(UserMapper.class.getName());
        User user = new User(22, "sunjinwei");
        cache.putObject("aaa", user);
        Object aaa = cache.getObject("aaa");
        System.out.println("====:" + aaa);

    }

    @Test
    public void lruCasheTest() {
        LruCache lruCache = new LruCache(new PerpetualCache("default"));
        int size = lruCache.getSize();
        System.out.println("========="+size);
        lruCache.putObject("aaaa","aaa");
        lruCache.putObject("bbb","bbb");
        lruCache.putObject("ccc","ccc");



    }

}

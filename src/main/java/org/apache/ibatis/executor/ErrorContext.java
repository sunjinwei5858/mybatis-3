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

/**
 * 使用了单例模式
 * ErrorContext 是线程级别的的单例，每个线程中有一个此对象的单例，用于记录该线程的执行环境的错误信息。
 * @author Clinton Begin
 */
public class ErrorContext {

    /**
     * 从方法名上可以得到，这是系统对象里的行分隔符.
     * system类不能手动创建对象，因为构造方法被私有化（即被private关键字修饰），组织外界创建对象(即不能用new关键字生成一个对象)。
     * System类中的都是静态方法（static关键字修饰），类名访问即可。
     * 在JDK中，有许多这样的类。
     * 在 System 类提供的设施中，有标准输入、标准输出和错误输出流；对外部定义的属性和环境变量的访问；加载文件和库的方法；还有快速复制数组的一部分的实用方法。
     *
     * 从JDK源码中可以得出：从JDK1.7（含）之后才开始有的这个方法，
     *
     * 在UNIX系统下，System.lineSeparator()方法返回 "\n"
     *
     * 在Windows系统下，System.lineSeparator()方法返回 "\r\n"
     *
     * 其实使用这个就实现了程序的跨平台运行，System.lineSeparator()方法会根据当前的系统返回对应的行分隔符。
     * 从而避免了你编写的程序在windows系统上可以运行，linux/unix系统上无法运行的情况。
     */
    private static final String LINE_SEPARATOR = System.lineSeparator();
    /**
     * 使用了ThreadLocal实例化对象 线程级别的单例。
     * 使用 private 修饰的 ThreadLocal 来保证每个线程拥有一个 ErrorContext 对象，
     * 在调用 instance() 方法时再从 ThreadLocal 中获取此单例对象。
     */
    private static final ThreadLocal<ErrorContext> LOCAL = ThreadLocal.withInitial(ErrorContext::new);

    private ErrorContext stored;
    private String resource;
    private String activity;
    private String object;
    private String message;
    private String sql;
    private Throwable cause;

    /**
     * 私有构造
     */
    private ErrorContext() {
    }

    public static ErrorContext instance() {
        return LOCAL.get();
    }

    public ErrorContext store() {
        ErrorContext newContext = new ErrorContext();
        newContext.stored = this;
        LOCAL.set(newContext);
        return LOCAL.get();
    }

    public ErrorContext recall() {
        if (stored != null) {
            LOCAL.set(stored);
            stored = null;
        }
        return LOCAL.get();
    }

    public ErrorContext resource(String resource) {
        this.resource = resource;
        return this;
    }

    public ErrorContext activity(String activity) {
        this.activity = activity;
        return this;
    }

    public ErrorContext object(String object) {
        this.object = object;
        return this;
    }

    public ErrorContext message(String message) {
        this.message = message;
        return this;
    }

    public ErrorContext sql(String sql) {
        this.sql = sql;
        return this;
    }

    public ErrorContext cause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public ErrorContext reset() {
        resource = null;
        activity = null;
        object = null;
        message = null;
        sql = null;
        cause = null;
        LOCAL.remove();
        return this;
    }

    @Override
    public String toString() {
        StringBuilder description = new StringBuilder();

        // message
        if (this.message != null) {
            description.append(LINE_SEPARATOR);
            description.append("### ");
            description.append(this.message);
        }

        // resource
        if (resource != null) {
            description.append(LINE_SEPARATOR);
            description.append("### The error may exist in ");
            description.append(resource);
        }

        // object
        if (object != null) {
            description.append(LINE_SEPARATOR);
            description.append("### The error may involve ");
            description.append(object);
        }

        // activity
        if (activity != null) {
            description.append(LINE_SEPARATOR);
            description.append("### The error occurred while ");
            description.append(activity);
        }

        // sql
        if (sql != null) {
            description.append(LINE_SEPARATOR);
            description.append("### SQL: ");
            description.append(sql.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim());
        }

        // cause
        if (cause != null) {
            description.append(LINE_SEPARATOR);
            description.append("### Cause: ");
            description.append(cause.toString());
        }

        return description.toString();
    }

}

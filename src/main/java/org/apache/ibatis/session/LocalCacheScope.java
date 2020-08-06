/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.session;

/**
 * LocalCache，也被称为一级缓存，有如下特点:
 *     它的生命周期与SqlSession一致。
 *     底层用HashMap实现，没有缓存内容更新和过期。
 *     有个多个SqlSession时，且有数据库写，会出现脏读的情况，一级缓存慎用，或者将Scope设置为Statement。
 * @author Eduardo Macarron
 */
public enum LocalCacheScope {
  SESSION,STATEMENT
}

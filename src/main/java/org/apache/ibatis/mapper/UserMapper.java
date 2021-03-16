package org.apache.ibatis.mapper;

import org.apache.ibatis.domain.User;

import java.util.List;

//@CacheNamespace(blocking = true) // 加上这行代码是为了增加一个责任链 BlockingCache
public interface UserMapper {

    List<User> findUserList();

    int updateName(User user);

    User findUserById(Integer userId);

}

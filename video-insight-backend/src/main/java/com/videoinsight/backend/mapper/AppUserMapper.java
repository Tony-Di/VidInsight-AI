package com.videoinsight.backend.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.videoinsight.backend.entity.AppUser;

public interface AppUserMapper extends BaseMapper<AppUser> {

    default AppUser findByEmail(String email) {
        return selectOne(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getEmail, email)
                        .last("LIMIT 1")
        );
    }
}

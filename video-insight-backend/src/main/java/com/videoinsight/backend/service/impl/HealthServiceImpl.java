package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.service.HealthService;
import org.springframework.stereotype.Service;

@Service
public class HealthServiceImpl implements HealthService {

    @Override
    public String check() {
        return "ok";
    }
}

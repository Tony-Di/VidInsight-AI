package com.videoinsight.backend.service;

public interface HealthService {

    String check();

    String checkRedis();

    String checkRedisson();
}

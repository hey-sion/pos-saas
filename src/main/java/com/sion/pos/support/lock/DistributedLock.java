package com.sion.pos.support.lock;

import java.time.Duration;
import java.util.Optional;

public interface DistributedLock {

    Optional<String> tryLock(String key, Duration ttl);

    void unlock(String key, String token);
}
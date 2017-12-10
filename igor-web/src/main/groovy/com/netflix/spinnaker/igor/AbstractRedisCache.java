/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor;

import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.HashSet;
import java.util.Set;

public class AbstractRedisCache {

    // TODO rz - configurable
    private final static int SCAN_SIZE = 1000;

    protected final RedisClientDelegate redisClientDelegate;

    public AbstractRedisCache(RedisClientDelegate redisClientDelegate) {
        this.redisClientDelegate = redisClientDelegate;
    }

    protected Set<String> scanAll(String glob) {
        return redisClientDelegate.withMultiClient(c -> {
            final Set<String> matches = new HashSet<>();
            final ScanParams scanParams = new ScanParams().count(SCAN_SIZE).match(glob);
            String cursor = "0";
            while (true) {
                final ScanResult<String> scanResult = c.scan(cursor, scanParams);
                matches.addAll(scanResult.getResult());
                cursor = scanResult.getStringCursor();
                if ("0".equals(cursor)) {
                    return matches;
                }
            }
        });
    }
}

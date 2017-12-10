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
package com.netflix.spinnaker.igor.build;

import com.netflix.spinnaker.igor.AbstractRedisCache;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared cache of build details
 */
@Service
public class BuildCache extends AbstractRedisCache {

    private final static String ID = "builds";

    private final IgorConfigurationProperties igorConfigurationProperties;

    @Autowired
    public BuildCache(RedisClientDelegate redisClientDelegate,
                      IgorConfigurationProperties igorConfigurationProperties) {
        super(redisClientDelegate);
        this.igorConfigurationProperties = igorConfigurationProperties;
    }

    public List<String> getJobNames(String master) {
        List<String> jobs = scanAll(baseKey() + ":completed:" + master + ":*")
            .stream()
            .map(BuildCache::extractJobName)
            .collect(Collectors.toList());
        jobs.sort(Comparator.naturalOrder());
        return jobs;
    }

    public List<String> getTypeaheadResults(String search) {
        List<String> results = scanAll(baseKey() + ":*:*:*" + search.toUpperCase() + "*:*")
            .stream()
            .map(BuildCache::extractTypeaheadResult)
            .collect(Collectors.toList());
        results.sort(Comparator.naturalOrder());
        return results;
    }

    public int getLastBuild(String master, String job, boolean running) {
        String key = makeKey(master, job, running);
        return redisClientDelegate.withCommandsClient(c -> {
            if (!c.exists(key)) {
                return -1;
            }
            return Integer.parseInt(c.get(key));
        });
    }

    public Long getTTL(String master, String job) {
        return redisClientDelegate.withCommandsClient(c -> {
            return c.ttl(makeKey(master, job));
        });
    }

    public void setTTL(String key, int ttl) {
        redisClientDelegate.withCommandsClient(c -> {
            c.expire(key, ttl);
        });
    }

    public void setLastBuild(String master, String job, int lastBuild, boolean building, int ttl) {
        if (!building) {
            setBuild(makeKey(master, job), lastBuild, false, master, job, ttl);
        }
        storeLastBuild(makeKey(master, job, building), lastBuild, ttl);
    }

    public List<String> getDeprecatedJobNames(String master) {
        List<String> jobs = scanAll(baseKey() + ":" + master + ":*")
            .stream()
            .map(BuildCache::extractDeprecatedJobName)
            .collect(Collectors.toList());
        jobs.sort(Comparator.naturalOrder());
        return jobs;
    }

    public Map getDeprecatedLastBuild(String master, String job) {
        String key = makeKey(master, job);
        Map<String, String> result = redisClientDelegate.withCommandsClient(c -> {
            if (!c.exists(key)) {
                return new HashMap<>();
            }
            return c.hgetAll(key);
        });

        if (result.isEmpty()) {
            return result;
        }

        Map<String, Object> converted = new HashMap<>();
        converted.put("lastBuildLabel", Integer.parseInt(result.get("lastBuildLabel")));
        converted.put("lastBuildBuilding", Boolean.valueOf(result.get("lastBuildBuilding")));

        return converted;
    }

    private void setBuild(String key, int lastBuild, boolean building, String master, String job, int ttl) {
        redisClientDelegate.withCommandsClient(c -> {
            c.hset(key, "lastBuildLabel", Integer.toString(lastBuild));
            c.hset(key, "lastBuildBuilding", Boolean.toString(building));
        });
        setTTL(key, ttl);
    }

    private void storeLastBuild(String key, int lastBuild, int ttl) {
        redisClientDelegate.withCommandsClient(c -> {
            c.set(key, Integer.toString(lastBuild));
        });
        setTTL(key, ttl);
    }

    protected String makeKey(String master, String job) {
        return baseKey() + ":" + master + ":" + job.toUpperCase() + ":" + job;
    }

    protected String makeKey(String master, String job, boolean running) {
        String buildState = running ? "running" : "completed";
        return baseKey() + ":" + buildState + ":" + master + ":" + job.toUpperCase() + ":" + job;
    }

    private static String extractJobName(String key) {
        return key.split(":")[5];
    }

    private static String extractDeprecatedJobName(String key) {
        return key.split(":")[4];
    }

    private static String extractTypeaheadResult(String key) {
        String[] parts = key.split(":");
        return parts[3] + ":" + parts[5];
    }

    private String baseKey() {
        return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix() + ":" + ID;
    }
}

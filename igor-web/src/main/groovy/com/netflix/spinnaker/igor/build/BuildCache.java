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

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Shared cache of build details */
@Service
public class BuildCache {

  private static final String ID = "builds";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  @Autowired
  public BuildCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  public List<String> getJobNames(String master) {
    List<String> jobs = new ArrayList<>();
    redisClientDelegate.withKeyScan(
        baseKey() + ":completed:" + master + ":*",
        1000,
        page ->
            jobs.addAll(
                page.getResults().stream()
                    .map(BuildCache::extractJobName)
                    .collect(Collectors.toList())));
    jobs.sort(Comparator.naturalOrder());
    return jobs;
  }

  public List<String> getTypeaheadResults(String search) {
    List<String> results = new ArrayList<>();
    redisClientDelegate.withKeyScan(
        baseKey() + ":*:*:*" + search.toUpperCase() + "*:*",
        1000,
        page ->
            results.addAll(
                page.getResults().stream()
                    .map(BuildCache::extractTypeaheadResult)
                    .collect(Collectors.toList())));
    results.sort(Comparator.naturalOrder());
    return results;
  }

  public int getLastBuild(String master, String job, boolean running) {
    String key = makeKey(master, job, running);
    return redisClientDelegate.withCommandsClient(
        c -> {
          if (!c.exists(key)) {
            return -1;
          }
          return Integer.parseInt(c.get(key));
        });
  }

  public Long getTTL(String master, String job) {
    final String key = makeKey(master, job);
    return getTTL(key);
  }

  private Long getTTL(String key) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          return c.ttl(key);
        });
  }

  public void setTTL(String key, int ttl) {
    redisClientDelegate.withCommandsClient(
        c -> {
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
    List<String> jobs = new ArrayList<>();
    redisClientDelegate.withKeyScan(
        baseKey() + ":" + master + ":*",
        1000,
        page ->
            jobs.addAll(
                page.getResults().stream()
                    .map(BuildCache::extractDeprecatedJobName)
                    .collect(Collectors.toList())));
    jobs.sort(Comparator.naturalOrder());
    return jobs;
  }

  public Map<String, Object> getDeprecatedLastBuild(String master, String job) {
    String key = makeKey(master, job);
    Map<String, String> result =
        redisClientDelegate.withCommandsClient(
            c -> {
              if (!c.exists(key)) {
                return null;
              }
              return c.hgetAll(key);
            });

    if (result == null) {
      return new HashMap<>();
    }

    Map<String, Object> converted = new HashMap<>();
    converted.put("lastBuildLabel", Integer.parseInt(result.get("lastBuildLabel")));
    converted.put("lastBuildBuilding", Boolean.valueOf(result.get("lastBuildBuilding")));

    return converted;
  }

  public List<Map<String, String>> getTrackedBuilds(String master) {
    List<Map<String, String>> builds =
        redisClientDelegate.withMultiClient(
            c -> {
              return c.keys(baseKey() + ":track:" + master + ":*").stream()
                  .map(BuildCache::getTrackedBuild)
                  .collect(Collectors.toList());
            });
    return builds;
  }

  public void setTracking(String master, String job, int buildId, int ttl) {
    String key = makeTrackKey(master, job, buildId);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.set(key, "marked as running");
        });
    setTTL(key, ttl);
  }

  public void deleteTracking(String master, String job, int buildId) {
    String key = makeTrackKey(master, job, buildId);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.del(key);
        });
  }

  private static Map<String, String> getTrackedBuild(String key) {
    Map<String, String> build = new HashMap<>();
    build.put("job", extractJobName(key));
    build.put("buildId", extractBuildIdFromTrackingKey(key));
    return build;
  }

  private void setBuild(
      String key, int lastBuild, boolean building, String master, String job, int ttl) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, "lastBuildLabel", Integer.toString(lastBuild));
          c.hset(key, "lastBuildBuilding", Boolean.toString(building));
        });
    setTTL(key, ttl);
  }

  private void storeLastBuild(String key, int lastBuild, int ttl) {
    redisClientDelegate.withCommandsClient(
        c -> {
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

  protected String makeTrackKey(String master, String job, int buildId) {
    return baseKey() + ":track:" + master + ":" + job.toUpperCase() + ":" + job + ":" + buildId;
  }

  private static String extractJobName(String key) {
    return key.split(":")[5];
  }

  private static String extractBuildIdFromTrackingKey(String key) {
    return key.split(":")[6];
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

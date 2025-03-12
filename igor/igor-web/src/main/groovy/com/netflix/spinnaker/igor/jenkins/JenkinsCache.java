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
package com.netflix.spinnaker.igor.jenkins;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Shared cache of build details for jenkins */
@Service
public class JenkinsCache {

  private static final String POLL_STAMP = "lastPollCycleTimestamp";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  @Autowired
  public JenkinsCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  public List<String> getJobNames(String master) {
    List<String> jobs = new ArrayList<>();
    redisClientDelegate.withKeyScan(
        prefix() + ":" + master + ":*",
        1000,
        page ->
            jobs.addAll(
                page.getResults().stream()
                    .map(JenkinsCache::extractJobName)
                    .collect(Collectors.toList())));
    jobs.sort(Comparator.naturalOrder());
    return jobs;
  }

  public List<String> getTypeaheadResults(String search) {
    List<String> results = new ArrayList<>();
    redisClientDelegate.withKeyScan(
        prefix() + ":*:*" + search.toUpperCase() + "*:*",
        1000,
        page ->
            results.addAll(
                page.getResults().stream()
                    .map(JenkinsCache::extractTypeaheadResult)
                    .collect(Collectors.toList())));
    results.sort(Comparator.naturalOrder());
    return results;
  }

  public Map<String, Object> getLastBuild(String master, String job) {
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

  public void setLastBuild(String master, String job, int lastBuild, boolean building) {
    String key = makeKey(master, job);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, "lastBuildLabel", Integer.toString(lastBuild));
          c.hset(key, "lastBuildBuilding", Boolean.toString(building));
        });
  }

  public void setLastPollCycleTimestamp(String master, String job, Long timestamp) {
    String key = makeKey(master, job);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, POLL_STAMP, Long.toString(timestamp));
        });
  }

  public Long getLastPollCycleTimestamp(String master, String job) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          String ts = c.hget(makeKey(master, job), POLL_STAMP);
          return ts == null ? null : Long.parseLong(ts);
        });
  }

  public Boolean getEventPosted(String master, String job, Long cursor, Integer buildNumber) {
    String key = makeKey(master, job) + ":" + POLL_STAMP + ":" + cursor;
    return redisClientDelegate.withCommandsClient(
        c -> c.hget(key, Integer.toString(buildNumber)) != null);
  }

  public void setEventPosted(String master, String job, Long cursor, Integer buildNumber) {
    String key = makeKey(master, job) + ":" + POLL_STAMP + ":" + cursor;
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, Integer.toString(buildNumber), "POSTED");
        });
  }

  public void pruneOldMarkers(String master, String job, Long cursor) {
    remove(master, job);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.del(makeKey(master, job) + ":" + POLL_STAMP + ":" + cursor);
        });
  }

  public void remove(String master, String job) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.del(makeKey(master, job));
        });
  }

  private String makeKey(String master, String job) {
    return prefix() + ":" + master + ":" + job.toUpperCase() + ":" + job;
  }

  private static String extractJobName(String key) {
    return key.split(":")[3];
  }

  private static String extractTypeaheadResult(String key) {
    String[] parts = key.split(":");
    return parts[1] + ":" + parts[3];
  }

  private String prefix() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix();
  }
}

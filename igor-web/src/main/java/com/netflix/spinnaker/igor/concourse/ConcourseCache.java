/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.concourse;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.concourse.client.model.Job;
import com.netflix.spinnaker.igor.config.ConcourseProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ConcourseCache {
  private static final String ID = "concourse:builds:queue";

  private static final String POLL_STAMP = "lastPollCycleTimestamp";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  public void setLastPollCycleTimestamp(ConcourseProperties.Host host, Job job, long timestamp) {
    String key = makeKey(host, job);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, POLL_STAMP, Long.toString(timestamp));
        });
  }

  public Long getLastPollCycleTimestamp(ConcourseProperties.Host host, Job job) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          String ts = c.hget(makeKey(host, job), POLL_STAMP);
          return ts == null ? null : Long.parseLong(ts);
        });
  }

  public boolean getEventPosted(
      ConcourseProperties.Host host, Job job, Long cursor, Integer buildNumber) {
    String key = makeKey(host, job) + ":" + POLL_STAMP + ":" + cursor;
    return redisClientDelegate.withCommandsClient(
        c -> c.hget(key, Integer.toString(buildNumber)) != null);
  }

  public void setEventPosted(
      ConcourseProperties.Host host, Job job, Long cursor, Integer buildNumber) {
    String key = makeKey(host, job) + ":" + POLL_STAMP + ":" + cursor;
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, Integer.toString(buildNumber), "POSTED");
        });
  }

  private String makeKey(ConcourseProperties.Host host, Job job) {
    return prefix()
        + ":"
        + host.getName()
        + ":"
        + job.getTeamName()
        + ":"
        + job.getPipelineName()
        + ":"
        + job.getName();
  }

  private String prefix() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix() + ":" + ID;
  }
}

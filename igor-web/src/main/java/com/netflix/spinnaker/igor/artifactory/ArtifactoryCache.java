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

package com.netflix.spinnaker.igor.artifactory;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.artifactory.model.ArtifactorySearch;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ArtifactoryCache {
  private static final String ID = "artifactory:publish:queue";

  private static final String POLL_STAMP = "lastPollCycleTimestamp";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  public void setLastPollCycleTimestamp(ArtifactorySearch search, long timestamp) {
    String key = makeKey(search);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, POLL_STAMP, Long.toString(timestamp));
        });
  }

  public Long getLastPollCycleTimestamp(ArtifactorySearch search) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          String ts = c.hget(makeKey(search), POLL_STAMP);
          return ts == null ? null : Long.parseLong(ts);
        });
  }

  private String makeKey(ArtifactorySearch search) {
    return prefix() + ":" + search.getPartitionName() + ":" + search.getGroupId();
  }

  private String prefix() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix() + ":" + ID;
  }
}

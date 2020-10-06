/*
 * Copyright 2020 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.helm.cache;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HelmCache {

  public static final String ID = "helm";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  @Autowired
  public HelmCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  public Set<String> getChartDigests(String account) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          return c.smembers(makeIndexKey(account));
        });
  }

  public void cacheChartDigests(String account, List<String> digests) {
    redisClientDelegate.withPipeline(
        p -> {
          for (String digest : digests) {
            p.sadd(makeIndexKey(account), digest);
          }
          redisClientDelegate.syncPipeline(p);
        });
  }

  public String makeMemberKey(String account, String digest) {
    return new HelmKey(prefix(), ID, account, digest).toString();
  }

  public String makeIndexKey(String account) {
    return makeMemberKey(account, null);
  }

  public String prefix() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix();
  }
}

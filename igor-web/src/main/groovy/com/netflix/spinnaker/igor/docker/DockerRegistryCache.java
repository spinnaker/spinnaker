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
package com.netflix.spinnaker.igor.docker;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DockerRegistryCache {

    private final static String ID = "dockerRegistry";

    // docker-digest must conform to hash:hashvalue. The string "~" explicitly avoids this to act as an "empty" placeholder.
    private final static String EMPTY_DIGEST = "~";

    private final RedisClientDelegate redisClientDelegate;
    private final IgorConfigurationProperties igorConfigurationProperties;

    @Autowired
    public DockerRegistryCache(RedisClientDelegate redisClientDelegate,
                               IgorConfigurationProperties igorConfigurationProperties) {
        this.redisClientDelegate = redisClientDelegate;
        this.igorConfigurationProperties = igorConfigurationProperties;
    }

    public List<String> getImages(String account) {
        return redisClientDelegate.withMultiClient(c -> {
            return new ArrayList<>(c.keys(prefix() + ":" + ID + ":" + account + "*"));
        });
    }

    public String getLastDigest(String account, String registry, String repository, String tag) {
        String key = makeKey(account, registry, repository, tag);
        return redisClientDelegate.withCommandsClient(c -> {
            Map<String, String> res = c.hgetAll(key);
            if (res.get("digest").equals(EMPTY_DIGEST)) {
                return null;
            }
            return res.get("digest");
        });
    }

    public void setLastDigest(String account, String registry, String repository, String tag, String digest) {
        String key = makeKey(account, registry, repository, tag);
        String d = digest == null ? EMPTY_DIGEST : digest;
        redisClientDelegate.withCommandsClient(c -> {
            c.hset(key, "digest", d);
        });
    }

    public void remove(String imageId) {
        redisClientDelegate.withCommandsClient(c -> {
            c.del(imageId);
        });
    }

    private String makeKey(String account, String registry, String repository, String tag) {
        return prefix() + ":" + ID + ":" + account + ":" + registry + ":" + repository + ":" + tag;
    }

    private String prefix() {
        return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix();
    }
}

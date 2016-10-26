/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.igor.docker

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

@SuppressWarnings(['PropertyName', 'DuplicateNumberLiteral'])
@Service
class DockerRegistryCache {
    @Autowired
    JedisPool jedisPool

    String id = 'dockerRegistry'

    // docker-digest must conform to hash:hashvalue. The string "~" explicitly avoids this to act as an "empty" placeholder.
    String emptyDigest = '~'

    @Autowired
    IgorConfigurationProperties igorConfigurationProperties

    List<String> getImages(String account) {
        jedisPool.resource.withCloseable { Jedis resource ->
            def key = "${prefix}:${id}:${account}*"

            return resource.keys(key).findAll { it } as List
        }
    }

    String getLastDigest(String account, String registry, String repository, String tag) {
        jedisPool.resource.withCloseable { Jedis resource ->
            def key = makeKey(account, registry, repository, tag)
            def res = resource.hgetAll(key).digest
            if (res == emptyDigest) {
                return null
            }
            return res
        }
    }

    void setLastDigest(String account, String registry, String repository, String tag, String digest) {
        jedisPool.resource.withCloseable { Jedis resource ->
            String key = makeKey(account, registry, repository, tag)
            digest = digest ?: emptyDigest
            resource.hset(key, 'digest', digest)
        }
    }

    void remove(String imageId) {
        jedisPool.resource.withCloseable { Jedis resource ->
            resource.del(imageId)
        }
    }

    public String makeKey(String account, String registry, String repository, String tag) {
        "${prefix}:${id}:${account}:${registry}:${repository}:${tag}"
    }

    private String getPrefix() {
        return igorConfigurationProperties.spinnaker.jedis.prefix
    }
}

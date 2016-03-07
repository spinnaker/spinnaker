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

import com.netflix.spinnaker.igor.docker.service.TaggedImage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

@SuppressWarnings(['PropertyName', 'DuplicateNumberLiteral'])
@Service
class DockerRegistryCache {
    @Autowired
    JedisPool jedisPool

    String id = 'dockerRegistry'

    @SuppressWarnings('GStringExpressionWithinString')
    @Value('${spinnaker.jedis.prefix:igor}')
    String prefix

    List<String> getImages(String account) {
        Jedis resource = jedisPool.resource
        def key = "${prefix}:${id}:${account}*"

        List<String> res = resource.keys(key).removeAll([null]) as List

        jedisPool.returnResource(resource)
        return res
    }

    String getLastDigest(String account, String registry, String repository, String tag) {
        Jedis resource = jedisPool.resource
        def key = makeKey(account, registry, repository, tag)
        def res = resource.hgetAll(key).digest

        jedisPool.returnResource(resource)
        return res
    }

    void setLastDigest(String account, String registry, String repository, String tag, String digest) {
        Jedis resource = jedisPool.resource
        String key = makeKey(account, registry, repository, tag)
        resource.hset(key, 'digest', digest)
        jedisPool.returnResource(resource)
    }

    void remove(String imageId) {
        Jedis resource = jedisPool.resource
        resource.del(imageId)
        jedisPool.returnResource(resource)
    }

    public String makeKey(String account, String registry, String repository, String tag) {
        "${prefix}:${id}:${account}:${registry}:${repository}:${tag}"
    }

    public Map<String, String> parseKey(String key) {
        def split = key?.split(":")

        if (!split || split[0] != prefix || split[1] != id || split.length != 6) {
            return null
        }

        [account: split[2], registry: split[3], repository: split[4], tag: split[5]]
    }
}

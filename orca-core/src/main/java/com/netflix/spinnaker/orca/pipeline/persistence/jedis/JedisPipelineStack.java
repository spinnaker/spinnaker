/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.orca.pipeline.persistence.jedis;

import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStack;

import java.util.ArrayList;
import java.util.List;

public class JedisPipelineStack implements PipelineStack {

  private RedisClientDelegate redisClientDelegate;
  private String prefix;

  public JedisPipelineStack(String prefix, RedisClientDelegate redisClientDelegate) {
    this.redisClientDelegate = redisClientDelegate;
    this.prefix = prefix;
  }

  public boolean addToListIfKeyExists(String id1, String id2, String content) {
    boolean result;
    // lua script here ensures that the add and check happens in one atomic operation
    result = redisClientDelegate.withScriptingClient(s -> {
      String script =
        "local key1 = KEYS[1];\n" +
        "local key2 = KEYS[2];\n" +
        "local value = ARGV[1];\n" +
        "if redis.call('exists', key1) == 1 then\n" +
        "  redis.call('lpush', key2, value);\n" +
          "  return 1;\n" +
        "end\n" +
          "return 0;";
      List<String> keys = new ArrayList<>();
      List<String> value = new ArrayList<>();
      keys.add(key(id1));
      keys.add(key(id2));
      value.add(content);
      Long response = ((Long) s.eval(script, keys, value));

      return response != null && response == 1L;
    });
    return result;
  }


  public void add(String id, String content) {
    redisClientDelegate.withCommandsClient(c -> {
      c.lpush(key(id), content);
    });
  }

  public void remove(String id, String content) {
    redisClientDelegate.withCommandsClient(c -> {
      c.lrem(key(id), 1, content);
    });
  }

  public boolean contains(String id) {
    Boolean result;
    result = redisClientDelegate.withCommandsClient(c -> {
      return c.exists(key(id));
    });
    return result;
  }

  public List<String> elements(String id) {
    return redisClientDelegate.withCommandsClient(c -> {
      return c.lrange(key(id), 0, -1);
    });
  }

  private String key(String id) {
    return String.format("%s:%s", prefix, id);
  }

}

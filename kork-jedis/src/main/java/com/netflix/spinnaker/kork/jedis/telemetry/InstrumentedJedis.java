/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.jedis.telemetry;

import com.netflix.spectator.api.Registry;
import redis.clients.jedis.*;
import redis.clients.jedis.params.geo.GeoRadiusParam;
import redis.clients.jedis.params.sortedset.ZAddParams;
import redis.clients.jedis.params.sortedset.ZIncrByParams;
import redis.clients.util.Pool;
import redis.clients.util.Slowlog;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.kork.jedis.telemetry.TelemetryHelper.*;

/**
 * Instruments:
 * <p>
 * - Timer for each command
 * - Distribution summary for all payload sizes
 * - Error rates
 * <p>
 */
public class InstrumentedJedis extends Jedis {

  private final Registry registry;
  private final Jedis delegated;
  private final String poolName;

  public InstrumentedJedis(Registry registry, Jedis delegated) {
    this(registry, delegated, "unnamed");
  }

  public InstrumentedJedis(Registry registry, Jedis delegated, String poolName) {
    this.registry = registry;
    this.delegated = delegated;
    this.poolName = poolName;
  }

  public Jedis unwrap() {
    return delegated;
  }

  @Override
  public String set(String key, String value) {
    String command = "set";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.set(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String set(String key, String value, String nxxx, String expx, long time) {
    String command = "set";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.set(key, value, nxxx, expx, time)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String get(String key) {
    String command = "get";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.get(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long exists(String... keys) {
    String command = "exists";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.exists(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean exists(String key) {
    String command = "exists";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.exists(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long del(String... keys) {
    String command = "del";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.del(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long del(String key) {
    String command = "del";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.del(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String type(String key) {
    String command = "type";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.type(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> keys(String pattern) {
    String command = "keys";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.keys(pattern)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String randomKey() {
    String command = "randomKey";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.randomKey()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String rename(String oldkey, String newkey) {
    String command = "rename";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rename(oldkey, newkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long renamenx(String oldkey, String newkey) {
    String command = "renamenx";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.renamenx(oldkey, newkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long expire(String key, int seconds) {
    String command = "expire";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.expire(key, seconds)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long expireAt(String key, long unixTime) {
    String command = "expireAt";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.expireAt(key, unixTime)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long ttl(String key) {
    String command = "ttl";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.ttl(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long move(String key, int dbIndex) {
    String command = "move";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.move(key, dbIndex)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String getSet(String key, String value) {
    String command = "getSet";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.getSet(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> mget(String... keys) {
    String command = "mget";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.mget(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long setnx(String key, String value) {
    String command = "setnx";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setnx(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String setex(String key, int seconds, String value) {
    String command = "setex";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setex(key, seconds, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String mset(String... keysvalues) {
    String command = "mset";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(keysvalues));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.mset(keysvalues)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long msetnx(String... keysvalues) {
    String command = "msetnx";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(keysvalues));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.msetnx(keysvalues)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long decrBy(String key, long integer) {
    String command = "decrBy";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.decrBy(key, integer)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long decr(String key) {
    String command = "decr";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.decr(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long incrBy(String key, long integer) {
    String command = "incrBy";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.incrBy(key, integer)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double incrByFloat(String key, double value) {
    String command = "incrByFloat";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.incrByFloat(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long incr(String key) {
    String command = "incr";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.incr(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long append(String key, String value) {
    String command = "append";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.append(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String substr(String key, int start, int end) {
    String command = "substr";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.substr(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hset(String key, String field, String value) {
    String command = "hset";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hset(key, field, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String hget(String key, String field) {
    String command = "hget";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hget(key, field)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hsetnx(String key, String field, String value) {
    String command = "hsetnx";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hsetnx(key, field, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String hmset(String key, Map<String, String> hash) {
    String command = "hmset";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(hash));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hmset(key, hash)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> hmget(String key, String... fields) {
    String command = "hmget";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hmget(key, fields)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hincrBy(String key, String field, long value) {
    String command = "hincrBy";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hincrBy(key, field, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double hincrByFloat(String key, String field, double value) {
    String command = "hincrByFloat";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hincrByFloat(key, field, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean hexists(String key, String field) {
    String command = "hexists";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hexists(key, field)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hdel(String key, String... fields) {
    String command = "hdel";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hdel(key, fields)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hlen(String key) {
    String command = "hlen";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hlen(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> hkeys(String key) {
    String command = "hkeys";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hkeys(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> hvals(String key) {
    String command = "hvals";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hvals(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Map<String, String> hgetAll(String key) {
    String command = "hgetAll";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hgetAll(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long rpush(String key, String... strings) {
    String command = "rpush";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(strings));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rpush(key, strings)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long lpush(String key, String... strings) {
    String command = "lpush";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(strings));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lpush(key, strings)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long llen(String key) {
    String command = "llen";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.llen(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> lrange(String key, long start, long end) {
    String command = "lrange";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lrange(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String ltrim(String key, long start, long end) {
    String command = "ltrim";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.ltrim(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String lindex(String key, long index) {
    String command = "lindex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lindex(key, index)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String lset(String key, long index, String value) {
    String command = "lset";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lset(key, index, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long lrem(String key, long count, String value) {
    String command = "lrem";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lrem(key, count, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String lpop(String key) {
    String command = "lpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lpop(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String rpop(String key) {
    String command = "rpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rpop(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String rpoplpush(String srckey, String dstkey) {
    String command = "rpoplpush";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rpoplpush(srckey, dstkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sadd(String key, String... members) {
    String command = "sadd";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(members));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sadd(key, members)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> smembers(String key) {
    String command = "smembers";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.smembers(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long srem(String key, String... members) {
    String command = "srem";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(members));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.srem(key, members)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String spop(String key) {
    String command = "spop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.spop(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> spop(String key, long count) {
    String command = "spop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.spop(key, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long smove(String srckey, String dstkey, String member) {
    String command = "smove";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.smove(srckey, dstkey, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long scard(String key) {
    String command = "scard";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scard(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean sismember(String key, String member) {
    String command = "sismember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sismember(key, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> sinter(String... keys) {
    String command = "sinter";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sinter(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sinterstore(String dstkey, String... keys) {
    String command = "sinterstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sinterstore(dstkey, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> sunion(String... keys) {
    String command = "sunion";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sunion(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sunionstore(String dstkey, String... keys) {
    String command = "sunionstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sunionstore(dstkey, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> sdiff(String... keys) {
    String command = "sdiff";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sdiff(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sdiffstore(String dstkey, String... keys) {
    String command = "sdiffstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sdiffstore(dstkey, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String srandmember(String key) {
    String command = "srandmember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.srandmember(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> srandmember(String key, int count) {
    String command = "srandmember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.srandmember(key, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zadd(String key, double score, String member) {
    String command = "zadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zadd(key, score, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zadd(String key, double score, String member, ZAddParams params) {
    String command = "zadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zadd(key, score, member, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zadd(String key, Map<String, Double> scoreMembers) {
    String command = "zadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zadd(key, scoreMembers)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
    String command = "zadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zadd(key, scoreMembers, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrange(String key, long start, long end) {
    String command = "zrange";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrange(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zrem(String key, String... members) {
    String command = "zrem";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrem(key, members)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double zincrby(String key, double score, String member) {
    String command = "zincrby";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zincrby(key, score, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double zincrby(String key, double score, String member, ZIncrByParams params) {
    String command = "zincrby";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zincrby(key, score, member, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zrank(String key, String member) {
    String command = "zrank";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrank(key, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zrevrank(String key, String member) {
    String command = "zrevrank";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrank(key, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrevrange(String key, long start, long end) {
    String command = "zrevrange";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrange(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeWithScores(String key, long start, long end) {
    String command = "zrangeWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeWithScores(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeWithScores(String key, long start, long end) {
    String command = "zrevrangeWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeWithScores(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zcard(String key) {
    String command = "zcard";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zcard(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double zscore(String key, String member) {
    String command = "zscore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zscore(key, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String watch(String... keys) {
    String command = "watch";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.watch(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> sort(String key) {
    String command = "sort";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sort(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> sort(String key, SortingParams sortingParameters) {
    String command = "sort";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sort(key, sortingParameters)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> blpop(int timeout, String... keys) {
    String command = "blpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.blpop(timeout, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> blpop(String... args) {
    String command = "blpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.blpop(args)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> brpop(String... args) {
    String command = "brpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.brpop(args)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public List<String> blpop(String arg) {
    String command = "blpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.blpop(arg)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public List<String> brpop(String arg) {
    String command = "brpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.brpop(arg)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sort(String key, SortingParams sortingParameters, String dstkey) {
    String command = "sort";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sort(key, sortingParameters, dstkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sort(String key, String dstkey) {
    String command = "sort";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sort(key, dstkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> brpop(int timeout, String... keys) {
    String command = "brpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.brpop(timeout, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zcount(String key, double min, double max) {
    String command = "zcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zcount(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zcount(String key, String min, String max) {
    String command = "zcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zcount(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrangeByScore(String key, double min, double max) {
    String command = "zrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScore(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrangeByScore(String key, String min, String max) {
    String command = "zrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScore(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrangeByScore(String key, double min, double max, int offset, int count) {
    String command = "zrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScore(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrangeByScore(String key, String min, String max, int offset, int count) {
    String command = "zrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScore(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max) {
    String command = "zrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScoreWithScores(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max) {
    String command = "zrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScoreWithScores(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScoreWithScores(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScoreWithScores(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrevrangeByScore(String key, double max, double min) {
    String command = "zrevrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScore(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrevrangeByScore(String key, String max, String min) {
    String command = "zrevrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScore(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrevrangeByScore(String key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScore(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min) {
    String command = "zrevrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScoreWithScores(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScoreWithScores(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScoreWithScores(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrevrangeByScore(String key, String max, String min, int offset, int count) {
    String command = "zrevrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScore(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min) {
    String command = "zrevrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScoreWithScores(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zremrangeByRank(String key, long start, long end) {
    String command = "zremrangeByRank";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zremrangeByRank(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zremrangeByScore(String key, double start, double end) {
    String command = "zremrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zremrangeByScore(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zremrangeByScore(String key, String start, String end) {
    String command = "zremrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zremrangeByScore(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zunionstore(String dstkey, String... sets) {
    String command = "zunionstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zunionstore(dstkey, sets)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zunionstore(String dstkey, ZParams params, String... sets) {
    String command = "zunionstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zunionstore(dstkey, params, sets)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zinterstore(String dstkey, String... sets) {
    String command = "zinterstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zinterstore(dstkey, sets)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zinterstore(String dstkey, ZParams params, String... sets) {
    String command = "zinterstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zinterstore(dstkey, params, sets)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zlexcount(String key, String min, String max) {
    String command = "zlexcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zlexcount(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrangeByLex(String key, String min, String max) {
    String command = "zrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByLex(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrangeByLex(String key, String min, String max, int offset, int count) {
    String command = "zrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByLex(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrevrangeByLex(String key, String max, String min) {
    String command = "zrevrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByLex(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<String> zrevrangeByLex(String key, String max, String min, int offset, int count) {
    String command = "zrevrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByLex(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zremrangeByLex(String key, String min, String max) {
    String command = "zremrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zremrangeByLex(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long strlen(String key) {
    String command = "strlen";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.strlen(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long lpushx(String key, String... string) {
    String command = "lpushx";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(string));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lpushx(key, string)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long persist(String key) {
    String command = "persist";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.persist(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long rpushx(String key, String... string) {
    String command = "rpushx";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(string));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rpushx(key, string)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String echo(String string) {
    String command = "echo";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.echo(string)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long linsert(String key, BinaryClient.LIST_POSITION where, String pivot, String value) {
    String command = "linsert";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.linsert(key, where, pivot, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String brpoplpush(String source, String destination, int timeout) {
    String command = "brpoplpush";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.brpoplpush(source, destination, timeout)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean setbit(String key, long offset, boolean value) {
    String command = "setbit";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setbit(key, offset, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean setbit(String key, long offset, String value) {
    String command = "setbit";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setbit(key, offset, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean getbit(String key, long offset) {
    String command = "getbit";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.getbit(key, offset)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long setrange(String key, long offset, String value) {
    String command = "setrange";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setrange(key, offset, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String getrange(String key, long startOffset, long endOffset) {
    String command = "getrange";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.getrange(key, startOffset, endOffset)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitpos(String key, boolean value) {
    String command = "bitpos";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitpos(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitpos(String key, boolean value, BitPosParams params) {
    String command = "bitpos";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitpos(key, value, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> configGet(String pattern) {
    String command = "configGet";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.configGet(pattern)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String configSet(String parameter, String value) {
    String command = "configSet";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.configSet(parameter, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object eval(String script, int keyCount, String... params) {
    String command = "eval";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.eval(script, keyCount, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long publish(String channel, String message) {
    String command = "publish";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.publish(channel, message)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object eval(String script, List<String> keys, List<String> args) {
    String command = "eval";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.eval(script, keys, args)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object eval(String script) {
    String command = "eval";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.eval(script)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object evalsha(String script) {
    String command = "evalsha";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.evalsha(script)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object evalsha(String sha1, List<String> keys, List<String> args) {
    String command = "evalsha";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.evalsha(sha1, keys, args)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object evalsha(String sha1, int keyCount, String... params) {
    String command = "evalsha";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.evalsha(sha1, keyCount, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean scriptExists(String sha1) {
    String command = "scriptExists";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scriptExists(sha1)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<Boolean> scriptExists(String... sha1) {
    String command = "scriptExists";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(sha1));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scriptExists(sha1)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String scriptLoad(String script) {
    String command = "scriptLoad";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scriptLoad(script)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitcount(String key) {
    String command = "bitcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitcount(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitcount(String key, long start, long end) {
    String command = "bitcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitcount(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitop(BitOP op, String destKey, String... srcKeys) {
    String command = "bitop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitop(op, destKey, srcKeys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<Map<String, String>> sentinelMasters() {
    String command = "sentinelMasters";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sentinelMasters()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> sentinelGetMasterAddrByName(String masterName) {
    String command = "sentinelGetMasterAddrByName";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sentinelGetMasterAddrByName(masterName)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sentinelReset(String pattern) {
    String command = "sentinelReset";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sentinelReset(pattern)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<Map<String, String>> sentinelSlaves(String masterName) {
    String command = "sentinelSlaves";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sentinelSlaves(masterName)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String sentinelFailover(String masterName) {
    String command = "sentinelFailover";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sentinelFailover(masterName)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String sentinelMonitor(String masterName, String ip, int port, int quorum) {
    String command = "sentinelMonitor";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sentinelMonitor(masterName, ip, port, quorum)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String sentinelRemove(String masterName) {
    String command = "sentinelRemove";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sentinelRemove(masterName)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String sentinelSet(String masterName, Map<String, String> parameterMap) {
    String command = "sentinelSet";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sentinelSet(masterName, parameterMap)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] dump(String key) {
    String command = "dump";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.dump(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String restore(String key, int ttl, byte[] serializedValue) {
    String command = "restore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.restore(key, ttl, serializedValue)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public Long pexpire(String key, int milliseconds) {
    String command = "pexpire";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pexpire(key, milliseconds)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long pexpire(String key, long milliseconds) {
    String command = "pexpire";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pexpire(key, milliseconds)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long pexpireAt(String key, long millisecondsTimestamp) {
    String command = "pexpireAt";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pexpireAt(key, millisecondsTimestamp)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long pttl(String key) {
    String command = "pttl";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pttl(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public String psetex(String key, int milliseconds, String value) {
    String command = "psetex";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.psetex(key, milliseconds, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String psetex(String key, long milliseconds, String value) {
    String command = "psetex";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.psetex(key, milliseconds, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String set(String key, String value, String nxxx) {
    String command = "set";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.set(key, value, nxxx)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String set(String key, String value, String nxxx, String expx, int time) {
    String command = "set";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.set(key, value, nxxx, expx, time)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clientKill(String client) {
    String command = "clientKill";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clientKill(client)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clientSetname(String name) {
    String command = "clientSetname";
    try {
      return registry.timer(timerId(registry, name, command)).record(() ->
        delegated.clientSetname(name)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, name)).increment();
      registry.counter(errorId(registry, name, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String migrate(String host, int port, String key, int destinationDb, int timeout) {
    String command = "migrate";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.migrate(host, port, key, destinationDb, timeout)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public ScanResult<String> scan(int cursor) {
    String command = "scan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scan(cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public ScanResult<String> scan(int cursor, ScanParams params) {
    String command = "scan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scan(cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public ScanResult<Map.Entry<String, String>> hscan(String key, int cursor) {
    String command = "hscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hscan(key, cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public ScanResult<Map.Entry<String, String>> hscan(String key, int cursor, ScanParams params) {
    String command = "hscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hscan(key, cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public ScanResult<String> sscan(String key, int cursor) {
    String command = "sscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sscan(key, cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public ScanResult<String> sscan(String key, int cursor, ScanParams params) {
    String command = "sscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sscan(key, cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public ScanResult<Tuple> zscan(String key, int cursor) {
    String command = "zscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zscan(key, cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public ScanResult<Tuple> zscan(String key, int cursor, ScanParams params) {
    String command = "zscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zscan(key, cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<String> scan(String cursor) {
    String command = "scan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scan(cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<String> scan(String cursor, ScanParams params) {
    String command = "scan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scan(cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor) {
    String command = "hscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hscan(key, cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
    String command = "hscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hscan(key, cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<String> sscan(String key, String cursor) {
    String command = "sscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sscan(key, cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
    String command = "sscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sscan(key, cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<Tuple> zscan(String key, String cursor) {
    String command = "zscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zscan(key, cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<Tuple> zscan(String key, String cursor, ScanParams params) {
    String command = "zscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zscan(key, cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterNodes() {
    String command = "clusterNodes";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterNodes()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String readonly() {
    String command = "readonly";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.readonly()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterMeet(String ip, int port) {
    String command = "clusterMeet";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterMeet(ip, port)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterReset(JedisCluster.Reset resetType) {
    String command = "clusterReset";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterReset(resetType)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterAddSlots(int... slots) {
    String command = "clusterAddSlots";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterAddSlots(slots)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterDelSlots(int... slots) {
    String command = "clusterDelSlots";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterDelSlots(slots)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterInfo() {
    String command = "clusterInfo";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterInfo()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> clusterGetKeysInSlot(int slot, int count) {
    String command = "clusterGetKeysInSlot";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterGetKeysInSlot(slot, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterSetSlotNode(int slot, String nodeId) {
    String command = "clusterSetSlotNode";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterSetSlotNode(slot, nodeId)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterSetSlotMigrating(int slot, String nodeId) {
    String command = "clusterSetSlotMigrating";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterSetSlotMigrating(slot, nodeId)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterSetSlotImporting(int slot, String nodeId) {
    String command = "clusterSetSlotImporting";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterSetSlotImporting(slot, nodeId)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterSetSlotStable(int slot) {
    String command = "clusterSetSlotStable";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterSetSlotStable(slot)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterForget(String nodeId) {
    String command = "clusterForget";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterForget(nodeId)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterFlushSlots() {
    String command = "clusterFlushSlots";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterFlushSlots()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long clusterKeySlot(String key) {
    String command = "clusterKeySlot";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterKeySlot(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long clusterCountKeysInSlot(int slot) {
    String command = "clusterCountKeysInSlot";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterCountKeysInSlot(slot)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterSaveConfig() {
    String command = "clusterSaveConfig";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterSaveConfig()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterReplicate(String nodeId) {
    String command = "clusterReplicate";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterReplicate(nodeId)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> clusterSlaves(String nodeId) {
    String command = "clusterSlaves";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterSlaves(nodeId)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clusterFailover() {
    String command = "clusterFailover";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterFailover()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<Object> clusterSlots() {
    String command = "clusterSlots";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clusterSlots()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String asking() {
    String command = "asking";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.asking()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> pubsubChannels(String pattern) {
    String command = "pubsubChannels";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pubsubChannels(pattern)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long pubsubNumPat() {
    String command = "pubsubNumPat";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pubsubNumPat()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Map<String, String> pubsubNumSub(String... channels) {
    String command = "pubsubNumSub";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pubsubNumSub(channels)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public void close() {
    String command = "close";
    delegated.close();
  }

  @Override
  public void setDataSource(Pool<Jedis> jedisPool) {
    String command = "setDataSource";
    delegated.setDataSource(jedisPool);
  }

  @Override
  public Long pfadd(String key, String... elements) {
    String command = "pfadd";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(elements));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pfadd(key, elements)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public long pfcount(String key) {
    String command = "pfcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pfcount(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public long pfcount(String... keys) {
    String command = "pfcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pfcount(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String pfmerge(String destkey, String... sourcekeys) {
    String command = "pfmerge";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pfmerge(destkey, sourcekeys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> blpop(int timeout, String key) {
    String command = "blpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.blpop(timeout, key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> brpop(int timeout, String key) {
    String command = "brpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.brpop(timeout, key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long geoadd(String key, double longitude, double latitude, String member) {
    String command = "geoadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geoadd(key, longitude, latitude, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long geoadd(String key, Map<String, GeoCoordinate> memberCoordinateMap) {
    String command = "geoadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geoadd(key, memberCoordinateMap)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double geodist(String key, String member1, String member2) {
    String command = "geodist";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geodist(key, member1, member2)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double geodist(String key, String member1, String member2, GeoUnit unit) {
    String command = "geodist";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geodist(key, member1, member2, unit)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> geohash(String key, String... members) {
    String command = "geohash";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(members));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geohash(key, members)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoCoordinate> geopos(String key, String... members) {
    String command = "geopos";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(members));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geopos(key, members)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoRadiusResponse> georadius(String key, double longitude, double latitude, double radius, GeoUnit unit) {
    String command = "georadius";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.georadius(key, longitude, latitude, radius, unit)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoRadiusResponse> georadius(String key, double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    String command = "georadius";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.georadius(key, longitude, latitude, radius, unit, param)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(String key, String member, double radius, GeoUnit unit) {
    String command = "georadiusByMember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.georadiusByMember(key, member, radius, unit)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(String key, String member, double radius, GeoUnit unit, GeoRadiusParam param) {
    String command = "georadiusByMember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.georadiusByMember(key, member, radius, unit, param)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<Long> bitfield(String key, String... arguments) {
    String command = "bitfield";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitfield(key, arguments)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String ping() {
    String command = "ping";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.ping()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String set(byte[] key, byte[] value) {
    String command = "set";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.set(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String set(byte[] key, byte[] value, byte[] nxxx, byte[] expx, long time) {
    String command = "set";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.set(key, value, nxxx, expx, time)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] get(byte[] key) {
    String command = "get";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.get(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String quit() {
    String command = "quit";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.quit()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long exists(byte[]... keys) {
    String command = "exists";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.exists(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean exists(byte[] key) {
    String command = "exists";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.exists(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long del(byte[]... keys) {
    String command = "del";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.del(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long del(byte[] key) {
    String command = "del";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.del(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String type(byte[] key) {
    String command = "type";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.type(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String flushDB() {
    String command = "flushDB";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.flushDB()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> keys(byte[] pattern) {
    String command = "keys";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.keys(pattern)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] randomBinaryKey() {
    String command = "randomBinaryKey";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.randomBinaryKey()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String rename(byte[] oldkey, byte[] newkey) {
    String command = "rename";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rename(oldkey, newkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long renamenx(byte[] oldkey, byte[] newkey) {
    String command = "renamenx";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.renamenx(oldkey, newkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long dbSize() {
    String command = "dbSize";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.dbSize()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long expire(byte[] key, int seconds) {
    String command = "expire";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.expire(key, seconds)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long expireAt(byte[] key, long unixTime) {
    String command = "expireAt";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.expireAt(key, unixTime)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long ttl(byte[] key) {
    String command = "ttl";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.ttl(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String select(int index) {
    String command = "select";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.select(index)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long move(byte[] key, int dbIndex) {
    String command = "move";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.move(key, dbIndex)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String flushAll() {
    String command = "flushAll";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.flushAll()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] getSet(byte[] key, byte[] value) {
    String command = "getSet";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.getSet(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> mget(byte[]... keys) {
    String command = "mget";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.mget(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long setnx(byte[] key, byte[] value) {
    String command = "setnx";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setnx(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String setex(byte[] key, int seconds, byte[] value) {
    String command = "setex";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setex(key, seconds, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String mset(byte[]... keysvalues) {
    String command = "mset";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.mset(keysvalues)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long msetnx(byte[]... keysvalues) {
    String command = "msetnx";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.msetnx(keysvalues)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long decrBy(byte[] key, long integer) {
    String command = "decrBy";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.decrBy(key, integer)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long decr(byte[] key) {
    String command = "decr";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.decr(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long incrBy(byte[] key, long integer) {
    String command = "incrBy";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.incrBy(key, integer)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double incrByFloat(byte[] key, double integer) {
    String command = "incrByFloat";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.incrByFloat(key, integer)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long incr(byte[] key) {
    String command = "incr";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.incr(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long append(byte[] key, byte[] value) {
    String command = "append";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.append(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] substr(byte[] key, int start, int end) {
    String command = "substr";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.substr(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hset(byte[] key, byte[] field, byte[] value) {
    String command = "hset";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hset(key, field, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] hget(byte[] key, byte[] field) {
    String command = "hget";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hget(key, field)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hsetnx(byte[] key, byte[] field, byte[] value) {
    String command = "hsetnx";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hsetnx(key, field, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String hmset(byte[] key, Map<byte[], byte[]> hash) {
    String command = "hmset";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hmset(key, hash)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> hmget(byte[] key, byte[]... fields) {
    String command = "hmget";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hmget(key, fields)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hincrBy(byte[] key, byte[] field, long value) {
    String command = "hincrBy";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hincrBy(key, field, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double hincrByFloat(byte[] key, byte[] field, double value) {
    String command = "hincrByFloat";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hincrByFloat(key, field, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean hexists(byte[] key, byte[] field) {
    String command = "hexists";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hexists(key, field)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hdel(byte[] key, byte[]... fields) {
    String command = "hdel";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hdel(key, fields)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long hlen(byte[] key) {
    String command = "hlen";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hlen(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> hkeys(byte[] key) {
    String command = "hkeys";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hkeys(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> hvals(byte[] key) {
    String command = "hvals";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hvals(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Map<byte[], byte[]> hgetAll(byte[] key) {
    String command = "hgetAll";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hgetAll(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long rpush(byte[] key, byte[]... strings) {
    String command = "rpush";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rpush(key, strings)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long lpush(byte[] key, byte[]... strings) {
    String command = "lpush";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lpush(key, strings)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long llen(byte[] key) {
    String command = "llen";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.llen(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> lrange(byte[] key, long start, long end) {
    String command = "lrange";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lrange(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String ltrim(byte[] key, long start, long end) {
    String command = "ltrim";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.ltrim(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] lindex(byte[] key, long index) {
    String command = "lindex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lindex(key, index)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String lset(byte[] key, long index, byte[] value) {
    String command = "lset";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lset(key, index, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long lrem(byte[] key, long count, byte[] value) {
    String command = "lrem";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lrem(key, count, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] lpop(byte[] key) {
    String command = "lpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lpop(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] rpop(byte[] key) {
    String command = "rpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rpop(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] rpoplpush(byte[] srckey, byte[] dstkey) {
    String command = "rpoplpush";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rpoplpush(srckey, dstkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sadd(byte[] key, byte[]... members) {
    String command = "sadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sadd(key, members)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> smembers(byte[] key) {
    String command = "smembers";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.smembers(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long srem(byte[] key, byte[]... member) {
    String command = "srem";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.srem(key, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] spop(byte[] key) {
    String command = "spop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.spop(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> spop(byte[] key, long count) {
    String command = "spop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.spop(key, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long smove(byte[] srckey, byte[] dstkey, byte[] member) {
    String command = "smove";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.smove(srckey, dstkey, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long scard(byte[] key) {
    String command = "scard";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scard(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean sismember(byte[] key, byte[] member) {
    String command = "sismember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sismember(key, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> sinter(byte[]... keys) {
    String command = "sinter";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sinter(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sinterstore(byte[] dstkey, byte[]... keys) {
    String command = "sinterstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sinterstore(dstkey, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> sunion(byte[]... keys) {
    String command = "sunion";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sunion(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sunionstore(byte[] dstkey, byte[]... keys) {
    String command = "sunionstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sunionstore(dstkey, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> sdiff(byte[]... keys) {
    String command = "sdiff";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sdiff(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sdiffstore(byte[] dstkey, byte[]... keys) {
    String command = "sdiffstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sdiffstore(dstkey, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] srandmember(byte[] key) {
    String command = "srandmember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.srandmember(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> srandmember(byte[] key, int count) {
    String command = "srandmember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.srandmember(key, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zadd(byte[] key, double score, byte[] member) {
    String command = "zadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zadd(key, score, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zadd(byte[] key, double score, byte[] member, ZAddParams params) {
    String command = "zadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zadd(key, score, member, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zadd(byte[] key, Map<byte[], Double> scoreMembers) {
    String command = "zadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zadd(key, scoreMembers)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zadd(byte[] key, Map<byte[], Double> scoreMembers, ZAddParams params) {
    String command = "zadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zadd(key, scoreMembers, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrange(byte[] key, long start, long end) {
    String command = "zrange";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrange(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zrem(byte[] key, byte[]... members) {
    String command = "zrem";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrem(key, members)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double zincrby(byte[] key, double score, byte[] member) {
    String command = "zincrby";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zincrby(key, score, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double zincrby(byte[] key, double score, byte[] member, ZIncrByParams params) {
    String command = "zincrby";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zincrby(key, score, member, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zrank(byte[] key, byte[] member) {
    String command = "zrank";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrank(key, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zrevrank(byte[] key, byte[] member) {
    String command = "zrevrank";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrank(key, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrevrange(byte[] key, long start, long end) {
    String command = "zrevrange";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrange(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeWithScores(byte[] key, long start, long end) {
    String command = "zrangeWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeWithScores(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeWithScores(byte[] key, long start, long end) {
    String command = "zrevrangeWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeWithScores(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zcard(byte[] key) {
    String command = "zcard";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zcard(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double zscore(byte[] key, byte[] member) {
    String command = "zscore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zscore(key, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Transaction multi() {
    String command = "multi";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.multi()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public List<Object> multi(TransactionBlock jedisTransaction) {
    String command = "multi";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.multi(jedisTransaction)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String watch(byte[]... keys) {
    String command = "watch";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.watch(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String unwatch() {
    String command = "unwatch";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.unwatch()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> sort(byte[] key) {
    String command = "sort";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sort(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> sort(byte[] key, SortingParams sortingParameters) {
    String command = "sort";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sort(key, sortingParameters)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> blpop(int timeout, byte[]... keys) {
    String command = "blpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.blpop(timeout, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sort(byte[] key, SortingParams sortingParameters, byte[] dstkey) {
    String command = "sort";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sort(key, sortingParameters, dstkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long sort(byte[] key, byte[] dstkey) {
    String command = "sort";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sort(key, dstkey)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> brpop(int timeout, byte[]... keys) {
    String command = "brpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.brpop(timeout, keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public List<byte[]> blpop(byte[] arg) {
    String command = "blpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.blpop(arg)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public List<byte[]> brpop(byte[] arg) {
    String command = "brpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.brpop(arg)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> blpop(byte[]... args) {
    String command = "blpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.blpop(args)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> brpop(byte[]... args) {
    String command = "brpop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.brpop(args)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String auth(String password) {
    String command = "auth";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.auth(password)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public List<Object> pipelined(PipelineBlock jedisPipeline) {
    String command = "pipelined";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pipelined(jedisPipeline)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Pipeline pipelined() {
    String command = "pipelined";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pipelined()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zcount(byte[] key, double min, double max) {
    String command = "zcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zcount(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zcount(byte[] key, byte[] min, byte[] max) {
    String command = "zcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zcount(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrangeByScore(byte[] key, double min, double max) {
    String command = "zrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScore(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrangeByScore(byte[] key, byte[] min, byte[] max) {
    String command = "zrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScore(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrangeByScore(byte[] key, double min, double max, int offset, int count) {
    String command = "zrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScore(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrangeByScore(byte[] key, byte[] min, byte[] max, int offset, int count) {
    String command = "zrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScore(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(byte[] key, double min, double max) {
    String command = "zrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScoreWithScores(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max) {
    String command = "zrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScoreWithScores(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(byte[] key, double min, double max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScoreWithScores(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByScoreWithScores(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrevrangeByScore(byte[] key, double max, double min) {
    String command = "zrevrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScore(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrevrangeByScore(byte[] key, byte[] max, byte[] min) {
    String command = "zrevrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScore(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrevrangeByScore(byte[] key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScore(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrevrangeByScore(byte[] key, byte[] max, byte[] min, int offset, int count) {
    String command = "zrevrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScore(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, double max, double min) {
    String command = "zrevrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScoreWithScores(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScoreWithScores(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min) {
    String command = "zrevrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScoreWithScores(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByScoreWithScores(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zremrangeByRank(byte[] key, long start, long end) {
    String command = "zremrangeByRank";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zremrangeByRank(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zremrangeByScore(byte[] key, double start, double end) {
    String command = "zremrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zremrangeByScore(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zremrangeByScore(byte[] key, byte[] start, byte[] end) {
    String command = "zremrangeByScore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zremrangeByScore(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zunionstore(byte[] dstkey, byte[]... sets) {
    String command = "zunionstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zunionstore(dstkey, sets)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zunionstore(byte[] dstkey, ZParams params, byte[]... sets) {
    String command = "zunionstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zunionstore(dstkey, params, sets)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zinterstore(byte[] dstkey, byte[]... sets) {
    String command = "zinterstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zinterstore(dstkey, sets)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zinterstore(byte[] dstkey, ZParams params, byte[]... sets) {
    String command = "zinterstore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zinterstore(dstkey, params, sets)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zlexcount(byte[] key, byte[] min, byte[] max) {
    String command = "zlexcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zlexcount(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrangeByLex(byte[] key, byte[] min, byte[] max) {
    String command = "zrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByLex(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrangeByLex(byte[] key, byte[] min, byte[] max, int offset, int count) {
    String command = "zrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrangeByLex(key, min, max, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrevrangeByLex(byte[] key, byte[] max, byte[] min) {
    String command = "zrevrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByLex(key, max, min)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Set<byte[]> zrevrangeByLex(byte[] key, byte[] max, byte[] min, int offset, int count) {
    String command = "zrevrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zrevrangeByLex(key, max, min, offset, count)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long zremrangeByLex(byte[] key, byte[] min, byte[] max) {
    String command = "zremrangeByLex";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zremrangeByLex(key, min, max)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String save() {
    String command = "save";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.save()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String bgsave() {
    String command = "bgsave";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bgsave()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String bgrewriteaof() {
    String command = "bgrewriteaof";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bgrewriteaof()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long lastsave() {
    String command = "lastsave";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lastsave()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String shutdown() {
    String command = "shutdown";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.shutdown()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String info() {
    String command = "info";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.info()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String info(String section) {
    String command = "info";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.info(section)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public void monitor(JedisMonitor jedisMonitor) {
    delegated.monitor(jedisMonitor);
  }

  @Override
  public String slaveof(String host, int port) {
    String command = "slaveof";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.slaveof(host, port)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String slaveofNoOne() {
    String command = "slaveofNoOne";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.slaveofNoOne()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> configGet(byte[] pattern) {
    String command = "configGet";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.configGet(pattern)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String configResetStat() {
    String command = "configResetStat";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.configResetStat()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] configSet(byte[] parameter, byte[] value) {
    String command = "configSet";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.configSet(parameter, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public boolean isConnected() {
    String command = "isConnected";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.isConnected()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long strlen(byte[] key) {
    String command = "strlen";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.strlen(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public void sync() {
    String command = "sync";
    try {
      registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sync()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long lpushx(byte[] key, byte[]... string) {
    String command = "lpushx";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.lpushx(key, string)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long persist(byte[] key) {
    String command = "persist";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.persist(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long rpushx(byte[] key, byte[]... string) {
    String command = "rpushx";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.rpushx(key, string)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] echo(byte[] string) {
    String command = "echo";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.echo(string)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long linsert(byte[] key, BinaryClient.LIST_POSITION where, byte[] pivot, byte[] value) {
    String command = "linsert";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.linsert(key, where, pivot, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String debug(DebugParams params) {
    String command = "debug";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.debug(params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Client getClient() {
    String command = "getClient";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.getClient()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] brpoplpush(byte[] source, byte[] destination, int timeout) {
    String command = "brpoplpush";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.brpoplpush(source, destination, timeout)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean setbit(byte[] key, long offset, boolean value) {
    String command = "setbit";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setbit(key, offset, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean setbit(byte[] key, long offset, byte[] value) {
    String command = "setbit";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setbit(key, offset, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Boolean getbit(byte[] key, long offset) {
    String command = "getbit";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.getbit(key, offset)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitpos(byte[] key, boolean value) {
    String command = "bitpos";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitpos(key, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitpos(byte[] key, boolean value, BitPosParams params) {
    String command = "bitpos";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitpos(key, value, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long setrange(byte[] key, long offset, byte[] value) {
    String command = "setrange";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.setrange(key, offset, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] getrange(byte[] key, long startOffset, long endOffset) {
    String command = "getrange";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.getrange(key, startOffset, endOffset)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long publish(byte[] channel, byte[] message) {
    String command = "publish";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.publish(channel, message)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public void subscribe(BinaryJedisPubSub jedisPubSub, byte[]... channels) {
    delegated.subscribe(jedisPubSub, channels);
  }

  @Override
  public void psubscribe(BinaryJedisPubSub jedisPubSub, byte[]... patterns) {
    delegated.psubscribe(jedisPubSub, patterns);
  }

  @Override
  public Object eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
    String command = "eval";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.eval(script, keys, args)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  public static byte[][] getParamsWithBinary(List<byte[]> keys, List<byte[]> args) {
    String command = "getParamsWithBinary";
    return BinaryJedis.getParamsWithBinary(keys, args);
  }

  @Override
  public Object eval(byte[] script, byte[] keyCount, byte[]... params) {
    String command = "eval";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.eval(script, keyCount, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object eval(byte[] script, int keyCount, byte[]... params) {
    String command = "eval";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.eval(script, keyCount, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object eval(byte[] script) {
    String command = "eval";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.eval(script)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object evalsha(byte[] sha1) {
    String command = "evalsha";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.evalsha(sha1)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
    String command = "evalsha";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.evalsha(sha1, keys, args)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Object evalsha(byte[] sha1, int keyCount, byte[]... params) {
    String command = "evalsha";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.evalsha(sha1, keyCount, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String scriptFlush() {
    String command = "scriptFlush";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scriptFlush()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long scriptExists(byte[] sha1) {
    String command = "scriptExists";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scriptExists(sha1)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<Long> scriptExists(byte[]... sha1) {
    String command = "scriptExists";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scriptExists(sha1)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] scriptLoad(byte[] script) {
    String command = "scriptLoad";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scriptLoad(script)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitcount(byte[] key) {
    String command = "bitcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitcount(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitcount(byte[] key, long start, long end) {
    String command = "bitcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitcount(key, start, end)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long bitop(BitOP op, byte[] destKey, byte[]... srcKeys) {
    String command = "bitop";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitop(op, destKey, srcKeys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public byte[] dump(byte[] key) {
    String command = "dump";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.dump(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String restore(byte[] key, int ttl, byte[] serializedValue) {
    String command = "restore";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.restore(key, ttl, serializedValue)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public Long pexpire(byte[] key, int milliseconds) {
    String command = "pexpire";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pexpire(key, milliseconds)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long pexpire(byte[] key, long milliseconds) {
    String command = "pexpire";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pexpire(key, milliseconds)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long pexpireAt(byte[] key, long millisecondsTimestamp) {
    String command = "pexpireAt";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pexpireAt(key, millisecondsTimestamp)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long pttl(byte[] key) {
    String command = "pttl";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pttl(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  @Deprecated
  public String psetex(byte[] key, int milliseconds, byte[] value) {
    String command = "psetex";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.psetex(key, milliseconds, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String psetex(byte[] key, long milliseconds, byte[] value) {
    String command = "psetex";
    registry.distributionSummary(TelemetryHelper.payloadSizeId(registry, poolName, command)).record(payloadSize(value));
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.psetex(key, milliseconds, value)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String set(byte[] key, byte[] value, byte[] nxxx) {
    String command = "set";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.set(key, value, nxxx)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String set(byte[] key, byte[] value, byte[] nxxx, byte[] expx, int time) {
    String command = "set";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.set(key, value, nxxx, expx, time)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clientKill(byte[] client) {
    String command = "clientKill";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clientKill(client)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clientGetname() {
    String command = "clientGetname";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clientGetname()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clientList() {
    String command = "clientList";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clientList()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String clientSetname(byte[] name) {
    String command = "clientSetname";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.clientSetname(name)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<String> time() {
    String command = "time";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.time()
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String migrate(byte[] host, int port, byte[] key, int destinationDb, int timeout) {
    String command = "migrate";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.migrate(host, port, key, destinationDb, timeout)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long waitReplicas(int replicas, long timeout) {
    String command = "waitReplicas";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.waitReplicas(replicas, timeout)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long pfadd(byte[] key, byte[]... elements) {
    String command = "pfadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pfadd(key, elements)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public long pfcount(byte[] key) {
    String command = "pfcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pfcount(key)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String pfmerge(byte[] destkey, byte[]... sourcekeys) {
    String command = "pfmerge";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pfmerge(destkey, sourcekeys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long pfcount(byte[]... keys) {
    String command = "pfcount";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.pfcount(keys)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<byte[]> scan(byte[] cursor) {
    String command = "scan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scan(cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<byte[]> scan(byte[] cursor, ScanParams params) {
    String command = "scan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.scan(cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<Map.Entry<byte[], byte[]>> hscan(byte[] key, byte[] cursor) {
    String command = "hscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hscan(key, cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<Map.Entry<byte[], byte[]>> hscan(byte[] key, byte[] cursor, ScanParams params) {
    String command = "hscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.hscan(key, cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<byte[]> sscan(byte[] key, byte[] cursor) {
    String command = "sscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sscan(key, cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<byte[]> sscan(byte[] key, byte[] cursor, ScanParams params) {
    String command = "sscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.sscan(key, cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<Tuple> zscan(byte[] key, byte[] cursor) {
    String command = "zscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zscan(key, cursor)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public ScanResult<Tuple> zscan(byte[] key, byte[] cursor, ScanParams params) {
    String command = "zscan";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.zscan(key, cursor, params)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long geoadd(byte[] key, double longitude, double latitude, byte[] member) {
    String command = "geoadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geoadd(key, longitude, latitude, member)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Long geoadd(byte[] key, Map<byte[], GeoCoordinate> memberCoordinateMap) {
    String command = "geoadd";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geoadd(key, memberCoordinateMap)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double geodist(byte[] key, byte[] member1, byte[] member2) {
    String command = "geodist";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geodist(key, member1, member2)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public Double geodist(byte[] key, byte[] member1, byte[] member2, GeoUnit unit) {
    String command = "geodist";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geodist(key, member1, member2, unit)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> geohash(byte[] key, byte[]... members) {
    String command = "geohash";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geohash(key, members)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoCoordinate> geopos(byte[] key, byte[]... members) {
    String command = "geopos";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.geopos(key, members)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoRadiusResponse> georadius(byte[] key, double longitude, double latitude, double radius, GeoUnit unit) {
    String command = "georadius";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.georadius(key, longitude, latitude, radius, unit)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoRadiusResponse> georadius(byte[] key, double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    String command = "georadius";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.georadius(key, longitude, latitude, radius, unit, param)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(byte[] key, byte[] member, double radius, GeoUnit unit) {
    String command = "georadiusByMember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.georadiusByMember(key, member, radius, unit)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(byte[] key, byte[] member, double radius, GeoUnit unit, GeoRadiusParam param) {
    String command = "georadiusByMember";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.georadiusByMember(key, member, radius, unit, param)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public List<byte[]> bitfield(byte[] key, byte[]... arguments) {
    String command = "bitfield";
    try {
      return registry.timer(timerId(registry, poolName, command)).record(() ->
        delegated.bitfield(key, arguments)
      );
    } catch (Exception e) {
      registry.counter(allErrorId(registry, poolName)).increment();
      registry.counter(errorId(registry, poolName, command)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public void subscribe(JedisPubSub jedisPubSub, String... channels) {
    delegated.subscribe(jedisPubSub, channels);
  }

  @Override
  public void psubscribe(JedisPubSub jedisPubSub, String... patterns) {
    delegated.psubscribe(jedisPubSub, patterns);
  }

  @Override
  public List<Slowlog> slowlogGet() {
    return delegated.slowlogGet();
  }

  @Override
  public List<Slowlog> slowlogGet(long entries) {
    return delegated.slowlogGet(entries);
  }

  @Override
  public Long objectRefcount(String string) {
    return delegated.objectRefcount(string);
  }

  @Override
  public String objectEncoding(String string) {
    return delegated.objectEncoding(string);
  }

  @Override
  public Long objectIdletime(String string) {
    return delegated.objectIdletime(string);
  }

  @Override
  public void connect() {
    delegated.connect();
  }

  @Override
  public void disconnect() {
    delegated.disconnect();
  }

  @Override
  public void resetState() {
    delegated.resetState();
  }

  @Override
  public Long getDB() {
    return delegated.getDB();
  }

  @Override
  public String scriptKill() {
    return delegated.scriptKill();
  }

  @Override
  public String slowlogReset() {
    return delegated.slowlogReset();
  }

  @Override
  public Long slowlogLen() {
    return delegated.slowlogLen();
  }

  @Override
  public List<byte[]> slowlogGetBinary() {
    return delegated.slowlogGetBinary();
  }

  @Override
  public List<byte[]> slowlogGetBinary(long entries) {
    return delegated.slowlogGetBinary(entries);
  }

  @Override
  public Long objectRefcount(byte[] key) {
    return delegated.objectRefcount(key);
  }

  @Override
  public byte[] objectEncoding(byte[] key) {
    return delegated.objectEncoding(key);
  }

  @Override
  public Long objectIdletime(byte[] key) {
    return delegated.objectIdletime(key);
  }
}

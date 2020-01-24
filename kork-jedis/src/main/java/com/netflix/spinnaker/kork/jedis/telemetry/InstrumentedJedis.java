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

import static com.netflix.spinnaker.kork.jedis.telemetry.TelemetryHelper.*;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileDistributionSummary;
import com.netflix.spectator.api.histogram.PercentileTimer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import redis.clients.jedis.*;
import redis.clients.jedis.params.GeoRadiusParam;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.params.ZIncrByParams;
import redis.clients.jedis.util.Slowlog;

/**
 * Instruments: - Timer for each command - Distribution summary for all payload sizes - Error rates
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

  private <T> T instrumented(String command, Callable<T> action) {
    return internalInstrumented(command, Optional.empty(), action);
  }

  private <T> T instrumented(String command, long payloadSize, Callable<T> action) {
    return internalInstrumented(command, Optional.of(payloadSize), action);
  }

  private <T> T internalInstrumented(
      String command, Optional<Long> payloadSize, Callable<T> action) {
    payloadSize.ifPresent(
        size ->
            PercentileDistributionSummary.get(
                    registry, payloadSizeId(registry, poolName, command, false))
                .record(size));
    try {
      return PercentileTimer.get(registry, timerId(registry, poolName, command, false))
          .record(
              () -> {
                T result = action.call();
                registry
                    .counter(invocationId(registry, poolName, command, false, true))
                    .increment();
                return result;
              });
    } catch (Exception e) {
      registry.counter(invocationId(registry, poolName, command, false, false)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  private void instrumented(String command, Runnable action) {
    internalInstrumented(command, Optional.empty(), action);
  }

  private void instrumented(String command, long payloadSize, Runnable action) {
    internalInstrumented(command, Optional.of(payloadSize), action);
  }

  private void internalInstrumented(String command, Optional<Long> payloadSize, Runnable action) {
    payloadSize.ifPresent(
        size ->
            PercentileDistributionSummary.get(
                    registry, payloadSizeId(registry, poolName, command, false))
                .record(size));
    try {
      PercentileTimer.get(registry, timerId(registry, poolName, command, false))
          .record(
              () -> {
                action.run();
                registry
                    .counter(invocationId(registry, poolName, command, false, true))
                    .increment();
              });
    } catch (Exception e) {
      registry.counter(invocationId(registry, poolName, command, false, false)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public String set(String key, String value) {
    String command = "set";
    return instrumented(command, payloadSize(value), () -> delegated.set(key, value));
  }

  @Override
  public String set(String key, String value, SetParams params) {
    String command = "set";
    return instrumented(command, payloadSize(value), () -> delegated.set(key, value, params));
  }

  @Override
  public String get(String key) {
    String command = "get";
    return instrumented(command, () -> delegated.get(key));
  }

  @Override
  public Long exists(String... keys) {
    String command = "exists";
    return instrumented(command, () -> delegated.exists(keys));
  }

  @Override
  public Boolean exists(String key) {
    String command = "exists";
    return instrumented(command, () -> delegated.exists(key));
  }

  @Override
  public Long del(String... keys) {
    String command = "del";
    return instrumented(command, () -> delegated.del(keys));
  }

  @Override
  public Long del(String key) {
    String command = "del";
    return instrumented(command, () -> delegated.del(key));
  }

  @Override
  public String type(String key) {
    String command = "type";
    return instrumented(command, () -> delegated.type(key));
  }

  @Override
  public Set<String> keys(String pattern) {
    String command = "keys";
    return instrumented(command, () -> delegated.keys(pattern));
  }

  @Override
  public String randomKey() {
    String command = "randomKey";
    return instrumented(command, () -> delegated.randomKey());
  }

  @Override
  public String rename(String oldkey, String newkey) {
    String command = "rename";
    return instrumented(command, () -> delegated.rename(oldkey, newkey));
  }

  @Override
  public Long renamenx(String oldkey, String newkey) {
    String command = "renamenx";
    return instrumented(command, () -> delegated.renamenx(oldkey, newkey));
  }

  @Override
  public Long expire(String key, int seconds) {
    String command = "expire";
    return instrumented(command, () -> delegated.expire(key, seconds));
  }

  @Override
  public Long expireAt(String key, long unixTime) {
    String command = "expireAt";
    return instrumented(command, () -> delegated.expireAt(key, unixTime));
  }

  @Override
  public Long ttl(String key) {
    String command = "ttl";
    return instrumented(command, () -> delegated.ttl(key));
  }

  @Override
  public Long move(String key, int dbIndex) {
    String command = "move";
    return instrumented(command, () -> delegated.move(key, dbIndex));
  }

  @Override
  public String getSet(String key, String value) {
    String command = "getSet";
    return instrumented(command, payloadSize(value), () -> delegated.getSet(key, value));
  }

  @Override
  public List<String> mget(String... keys) {
    String command = "mget";
    return instrumented(command, () -> delegated.mget(keys));
  }

  @Override
  public Long setnx(String key, String value) {
    String command = "setnx";
    return instrumented(command, payloadSize(value), () -> delegated.setnx(key, value));
  }

  @Override
  public String setex(String key, int seconds, String value) {
    String command = "setex";
    return instrumented(command, payloadSize(value), () -> delegated.setex(key, seconds, value));
  }

  @Override
  public String mset(String... keysvalues) {
    String command = "mset";
    return instrumented(command, payloadSize(keysvalues), () -> delegated.mset(keysvalues));
  }

  @Override
  public Long msetnx(String... keysvalues) {
    String command = "msetnx";
    return instrumented(command, payloadSize(keysvalues), () -> delegated.msetnx(keysvalues));
  }

  @Override
  public Long decrBy(String key, long integer) {
    String command = "decrBy";
    return instrumented(command, () -> delegated.decrBy(key, integer));
  }

  @Override
  public Long decr(String key) {
    String command = "decr";
    return instrumented(command, () -> delegated.decr(key));
  }

  @Override
  public Long incrBy(String key, long integer) {
    String command = "incrBy";
    return instrumented(command, () -> delegated.incrBy(key, integer));
  }

  @Override
  public Double incrByFloat(String key, double value) {
    String command = "incrByFloat";
    return instrumented(command, () -> delegated.incrByFloat(key, value));
  }

  @Override
  public Long incr(String key) {
    String command = "incr";
    return instrumented(command, () -> delegated.incr(key));
  }

  @Override
  public Long append(String key, String value) {
    String command = "append";
    return instrumented(command, payloadSize(value), () -> delegated.append(key, value));
  }

  @Override
  public String substr(String key, int start, int end) {
    String command = "substr";
    return instrumented(command, () -> delegated.substr(key, start, end));
  }

  @Override
  public Long hset(String key, String field, String value) {
    String command = "hset";
    return instrumented(command, payloadSize(value), () -> delegated.hset(key, field, value));
  }

  @Override
  public String hget(String key, String field) {
    String command = "hget";
    return instrumented(command, () -> delegated.hget(key, field));
  }

  @Override
  public Long hsetnx(String key, String field, String value) {
    String command = "hsetnx";
    return instrumented(command, payloadSize(value), () -> delegated.hsetnx(key, field, value));
  }

  @Override
  public String hmset(String key, Map<String, String> hash) {
    String command = "hmset";
    return instrumented(command, payloadSize(hash), () -> delegated.hmset(key, hash));
  }

  @Override
  public List<String> hmget(String key, String... fields) {
    String command = "hmget";
    return instrumented(command, () -> delegated.hmget(key, fields));
  }

  @Override
  public Long hincrBy(String key, String field, long value) {
    String command = "hincrBy";
    return instrumented(command, () -> delegated.hincrBy(key, field, value));
  }

  @Override
  public Double hincrByFloat(String key, String field, double value) {
    String command = "hincrByFloat";
    return instrumented(command, () -> delegated.hincrByFloat(key, field, value));
  }

  @Override
  public Boolean hexists(String key, String field) {
    String command = "hexists";
    return instrumented(command, () -> delegated.hexists(key, field));
  }

  @Override
  public Long hdel(String key, String... fields) {
    String command = "hdel";
    return instrumented(command, () -> delegated.hdel(key, fields));
  }

  @Override
  public Long hlen(String key) {
    String command = "hlen";
    return instrumented(command, () -> delegated.hlen(key));
  }

  @Override
  public Set<String> hkeys(String key) {
    String command = "hkeys";
    return instrumented(command, () -> delegated.hkeys(key));
  }

  @Override
  public List<String> hvals(String key) {
    String command = "hvals";
    return instrumented(command, () -> delegated.hvals(key));
  }

  @Override
  public Map<String, String> hgetAll(String key) {
    String command = "hgetAll";
    return instrumented(command, () -> delegated.hgetAll(key));
  }

  @Override
  public Long rpush(String key, String... strings) {
    String command = "rpush";
    return instrumented(command, payloadSize(strings), () -> delegated.rpush(key, strings));
  }

  @Override
  public Long lpush(String key, String... strings) {
    String command = "lpush";
    return instrumented(command, payloadSize(strings), () -> delegated.lpush(key, strings));
  }

  @Override
  public Long llen(String key) {
    String command = "llen";
    return instrumented(command, () -> delegated.llen(key));
  }

  @Override
  public List<String> lrange(String key, long start, long end) {
    String command = "lrange";
    return instrumented(command, () -> delegated.lrange(key, start, end));
  }

  @Override
  public String ltrim(String key, long start, long end) {
    String command = "ltrim";
    return instrumented(command, () -> delegated.ltrim(key, start, end));
  }

  @Override
  public String lindex(String key, long index) {
    String command = "lindex";
    return instrumented(command, () -> delegated.lindex(key, index));
  }

  @Override
  public String lset(String key, long index, String value) {
    String command = "lset";
    return instrumented(command, payloadSize(value), () -> delegated.lset(key, index, value));
  }

  @Override
  public Long lrem(String key, long count, String value) {
    String command = "lrem";
    return instrumented(command, payloadSize(value), () -> delegated.lrem(key, count, value));
  }

  @Override
  public String lpop(String key) {
    String command = "lpop";
    return instrumented(command, () -> delegated.lpop(key));
  }

  @Override
  public String rpop(String key) {
    String command = "rpop";
    return instrumented(command, () -> delegated.rpop(key));
  }

  @Override
  public String rpoplpush(String srckey, String dstkey) {
    String command = "rpoplpush";
    return instrumented(command, () -> delegated.rpoplpush(srckey, dstkey));
  }

  @Override
  public Long sadd(String key, String... members) {
    String command = "sadd";
    return instrumented(command, payloadSize(members), () -> delegated.sadd(key, members));
  }

  @Override
  public Set<String> smembers(String key) {
    String command = "smembers";
    return instrumented(command, () -> delegated.smembers(key));
  }

  @Override
  public Long srem(String key, String... members) {
    String command = "srem";
    return instrumented(command, payloadSize(members), () -> delegated.srem(key, members));
  }

  @Override
  public String spop(String key) {
    String command = "spop";
    return instrumented(command, () -> delegated.spop(key));
  }

  @Override
  public Set<String> spop(String key, long count) {
    String command = "spop";
    return instrumented(command, () -> delegated.spop(key, count));
  }

  @Override
  public Long smove(String srckey, String dstkey, String member) {
    String command = "smove";
    return instrumented(command, () -> delegated.smove(srckey, dstkey, member));
  }

  @Override
  public Long scard(String key) {
    String command = "scard";
    return instrumented(command, () -> delegated.scard(key));
  }

  @Override
  public Boolean sismember(String key, String member) {
    String command = "sismember";
    return instrumented(command, () -> delegated.sismember(key, member));
  }

  @Override
  public Set<String> sinter(String... keys) {
    String command = "sinter";
    return instrumented(command, () -> delegated.sinter(keys));
  }

  @Override
  public Long sinterstore(String dstkey, String... keys) {
    String command = "sinterstore";
    return instrumented(command, () -> delegated.sinterstore(dstkey, keys));
  }

  @Override
  public Set<String> sunion(String... keys) {
    String command = "sunion";
    return instrumented(command, () -> delegated.sunion(keys));
  }

  @Override
  public Long sunionstore(String dstkey, String... keys) {
    String command = "sunionstore";
    return instrumented(command, () -> delegated.sunionstore(dstkey, keys));
  }

  @Override
  public Set<String> sdiff(String... keys) {
    String command = "sdiff";
    return instrumented(command, () -> delegated.sdiff(keys));
  }

  @Override
  public Long sdiffstore(String dstkey, String... keys) {
    String command = "sdiffstore";
    return instrumented(command, () -> delegated.sdiffstore(dstkey, keys));
  }

  @Override
  public String srandmember(String key) {
    String command = "srandmember";
    return instrumented(command, () -> delegated.srandmember(key));
  }

  @Override
  public List<String> srandmember(String key, int count) {
    String command = "srandmember";
    return instrumented(command, () -> delegated.srandmember(key, count));
  }

  @Override
  public Long zadd(String key, double score, String member) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, score, member));
  }

  @Override
  public Long zadd(String key, double score, String member, ZAddParams params) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, score, member, params));
  }

  @Override
  public Long zadd(String key, Map<String, Double> scoreMembers) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, scoreMembers));
  }

  @Override
  public Long zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, scoreMembers, params));
  }

  @Override
  public Set<String> zrange(String key, long start, long end) {
    String command = "zrange";
    return instrumented(command, () -> delegated.zrange(key, start, end));
  }

  @Override
  public Long zrem(String key, String... members) {
    String command = "zrem";
    return instrumented(command, () -> delegated.zrem(key, members));
  }

  @Override
  public Double zincrby(String key, double score, String member) {
    String command = "zincrby";
    return instrumented(command, () -> delegated.zincrby(key, score, member));
  }

  @Override
  public Double zincrby(String key, double score, String member, ZIncrByParams params) {
    String command = "zincrby";
    return instrumented(command, () -> delegated.zincrby(key, score, member, params));
  }

  @Override
  public Long zrank(String key, String member) {
    String command = "zrank";
    return instrumented(command, () -> delegated.zrank(key, member));
  }

  @Override
  public Long zrevrank(String key, String member) {
    String command = "zrevrank";
    return instrumented(command, () -> delegated.zrevrank(key, member));
  }

  @Override
  public Set<String> zrevrange(String key, long start, long end) {
    String command = "zrevrange";
    return instrumented(command, () -> delegated.zrevrange(key, start, end));
  }

  @Override
  public Set<Tuple> zrangeWithScores(String key, long start, long end) {
    String command = "zrangeWithScores";
    return instrumented(command, () -> delegated.zrangeWithScores(key, start, end));
  }

  @Override
  public Set<Tuple> zrevrangeWithScores(String key, long start, long end) {
    String command = "zrevrangeWithScores";
    return instrumented(command, () -> delegated.zrevrangeWithScores(key, start, end));
  }

  @Override
  public Long zcard(String key) {
    String command = "zcard";
    return instrumented(command, () -> delegated.zcard(key));
  }

  @Override
  public Double zscore(String key, String member) {
    String command = "zscore";
    return instrumented(command, () -> delegated.zscore(key, member));
  }

  @Override
  public String watch(String... keys) {
    String command = "watch";
    return instrumented(command, () -> delegated.watch(keys));
  }

  @Override
  public List<String> sort(String key) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key));
  }

  @Override
  public List<String> sort(String key, SortingParams sortingParameters) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, sortingParameters));
  }

  @Override
  public List<String> blpop(int timeout, String... keys) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(timeout, keys));
  }

  @Override
  public List<String> blpop(String... args) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(args));
  }

  @Override
  public List<String> brpop(String... args) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(args));
  }

  @Override
  public Long sort(String key, SortingParams sortingParameters, String dstkey) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, sortingParameters, dstkey));
  }

  @Override
  public Long sort(String key, String dstkey) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, dstkey));
  }

  @Override
  public List<String> brpop(int timeout, String... keys) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(timeout, keys));
  }

  @Override
  public Long zcount(String key, double min, double max) {
    String command = "zcount";
    return instrumented(command, () -> delegated.zcount(key, min, max));
  }

  @Override
  public Long zcount(String key, String min, String max) {
    String command = "zcount";
    return instrumented(command, () -> delegated.zcount(key, min, max));
  }

  @Override
  public Set<String> zrangeByScore(String key, double min, double max) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max));
  }

  @Override
  public Set<String> zrangeByScore(String key, String min, String max) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max));
  }

  @Override
  public Set<String> zrangeByScore(String key, double min, double max, int offset, int count) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Set<String> zrangeByScore(String key, String min, String max, int offset, int count) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max) {
    String command = "zrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max) {
    String command = "zrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(
      String key, double min, double max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(
      String key, String min, String max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Set<String> zrevrangeByScore(String key, double max, double min) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min));
  }

  @Override
  public Set<String> zrevrangeByScore(String key, String max, String min) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min));
  }

  @Override
  public Set<String> zrevrangeByScore(String key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(
      String key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(
      String key, String max, String min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Set<String> zrevrangeByScore(String key, String max, String min, int offset, int count) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Long zremrangeByRank(String key, long start, long end) {
    String command = "zremrangeByRank";
    return instrumented(command, () -> delegated.zremrangeByRank(key, start, end));
  }

  @Override
  public Long zremrangeByScore(String key, double start, double end) {
    String command = "zremrangeByScore";
    return instrumented(command, () -> delegated.zremrangeByScore(key, start, end));
  }

  @Override
  public Long zremrangeByScore(String key, String start, String end) {
    String command = "zremrangeByScore";
    return instrumented(command, () -> delegated.zremrangeByScore(key, start, end));
  }

  @Override
  public Long zunionstore(String dstkey, String... sets) {
    String command = "zunionstore";
    return instrumented(command, () -> delegated.zunionstore(dstkey, sets));
  }

  @Override
  public Long zunionstore(String dstkey, ZParams params, String... sets) {
    String command = "zunionstore";
    return instrumented(command, () -> delegated.zunionstore(dstkey, params, sets));
  }

  @Override
  public Long zinterstore(String dstkey, String... sets) {
    String command = "zinterstore";
    return instrumented(command, () -> delegated.zinterstore(dstkey, sets));
  }

  @Override
  public Long zinterstore(String dstkey, ZParams params, String... sets) {
    String command = "zinterstore";
    return instrumented(command, () -> delegated.zinterstore(dstkey, params, sets));
  }

  @Override
  public Long zlexcount(String key, String min, String max) {
    String command = "zlexcount";
    return instrumented(command, () -> delegated.zlexcount(key, min, max));
  }

  @Override
  public Set<String> zrangeByLex(String key, String min, String max) {
    String command = "zrangeByLex";
    return instrumented(command, () -> delegated.zrangeByLex(key, min, max));
  }

  @Override
  public Set<String> zrangeByLex(String key, String min, String max, int offset, int count) {
    String command = "zrangeByLex";
    return instrumented(command, () -> delegated.zrangeByLex(key, min, max, offset, count));
  }

  @Override
  public Set<String> zrevrangeByLex(String key, String max, String min) {
    String command = "zrevrangeByLex";
    return instrumented(command, () -> delegated.zrevrangeByLex(key, max, min));
  }

  @Override
  public Set<String> zrevrangeByLex(String key, String max, String min, int offset, int count) {
    String command = "zrevrangeByLex";
    return instrumented(command, () -> delegated.zrevrangeByLex(key, max, min, offset, count));
  }

  @Override
  public Long zremrangeByLex(String key, String min, String max) {
    String command = "zremrangeByLex";
    return instrumented(command, () -> delegated.zremrangeByLex(key, min, max));
  }

  @Override
  public Long strlen(String key) {
    String command = "strlen";
    return instrumented(command, () -> delegated.strlen(key));
  }

  @Override
  public Long lpushx(String key, String... string) {
    String command = "lpushx";
    return instrumented(command, payloadSize(string), () -> delegated.lpushx(key, string));
  }

  @Override
  public Long persist(String key) {
    String command = "persist";
    return instrumented(command, () -> delegated.persist(key));
  }

  @Override
  public Long rpushx(String key, String... string) {
    String command = "rpushx";
    return instrumented(command, payloadSize(string), () -> delegated.rpushx(key, string));
  }

  @Override
  public String echo(String string) {
    String command = "echo";
    return instrumented(command, () -> delegated.echo(string));
  }

  @Override
  public Long linsert(
      final String key, final ListPosition where, final String pivot, final String value) {
    String command = "linsert";
    return instrumented(
        command, payloadSize(value), () -> delegated.linsert(key, where, pivot, value));
  }

  @Override
  public String brpoplpush(String source, String destination, int timeout) {
    String command = "brpoplpush";
    return instrumented(command, () -> delegated.brpoplpush(source, destination, timeout));
  }

  @Override
  public Boolean setbit(String key, long offset, boolean value) {
    String command = "setbit";
    return instrumented(command, () -> delegated.setbit(key, offset, value));
  }

  @Override
  public Boolean setbit(String key, long offset, String value) {
    String command = "setbit";
    return instrumented(command, payloadSize(value), () -> delegated.setbit(key, offset, value));
  }

  @Override
  public Boolean getbit(String key, long offset) {
    String command = "getbit";
    return instrumented(command, () -> delegated.getbit(key, offset));
  }

  @Override
  public Long setrange(String key, long offset, String value) {
    String command = "setrange";
    return instrumented(command, payloadSize(value), () -> delegated.setrange(key, offset, value));
  }

  @Override
  public String getrange(String key, long startOffset, long endOffset) {
    String command = "getrange";
    return instrumented(command, () -> delegated.getrange(key, startOffset, endOffset));
  }

  @Override
  public Long bitpos(String key, boolean value) {
    String command = "bitpos";
    return instrumented(command, () -> delegated.bitpos(key, value));
  }

  @Override
  public Long bitpos(String key, boolean value, BitPosParams params) {
    String command = "bitpos";
    return instrumented(command, () -> delegated.bitpos(key, value, params));
  }

  @Override
  public List<String> configGet(String pattern) {
    String command = "configGet";
    return instrumented(command, () -> delegated.configGet(pattern));
  }

  @Override
  public String configSet(String parameter, String value) {
    String command = "configSet";
    return instrumented(command, () -> delegated.configSet(parameter, value));
  }

  @Override
  public Object eval(String script, int keyCount, String... params) {
    String command = "eval";
    return instrumented(
        command,
        payloadSize(script) + payloadSize(params),
        () -> delegated.eval(script, keyCount, params));
  }

  @Override
  public void subscribe(JedisPubSub jedisPubSub, String... channels) {
    delegated.subscribe(jedisPubSub, channels);
  }

  @Override
  public Long publish(String channel, String message) {
    String command = "publish";
    return instrumented(command, payloadSize(message), () -> delegated.publish(channel, message));
  }

  @Override
  public void psubscribe(JedisPubSub jedisPubSub, String... patterns) {
    delegated.psubscribe(jedisPubSub, patterns);
  }

  @Override
  public Object eval(String script, List<String> keys, List<String> args) {
    String command = "eval";
    return instrumented(
        command, payloadSize(script) + payloadSize(args), () -> delegated.eval(script, keys, args));
  }

  @Override
  public Object eval(String script) {
    String command = "eval";
    return instrumented(command, payloadSize(script), () -> delegated.eval(script));
  }

  @Override
  public Object evalsha(String script) {
    String command = "evalsha";
    return instrumented(command, () -> delegated.evalsha(script));
  }

  @Override
  public Object evalsha(String sha1, List<String> keys, List<String> args) {
    String command = "evalsha";
    return instrumented(command, payloadSize(args), () -> delegated.evalsha(sha1, keys, args));
  }

  @Override
  public Object evalsha(String sha1, int keyCount, String... params) {
    String command = "evalsha";
    return instrumented(
        command, payloadSize(params), () -> delegated.evalsha(sha1, keyCount, params));
  }

  @Override
  public Boolean scriptExists(String sha1) {
    String command = "scriptExists";
    return instrumented(command, () -> delegated.scriptExists(sha1));
  }

  @Override
  public List<Boolean> scriptExists(String... sha1) {
    String command = "scriptExists";
    return instrumented(command, () -> delegated.scriptExists(sha1));
  }

  @Override
  public String scriptLoad(String script) {
    String command = "scriptLoad";
    return instrumented(command, payloadSize(script), () -> delegated.scriptLoad(script));
  }

  @Override
  public List<Slowlog> slowlogGet() {
    String command = "slowlogGet";
    return instrumented(command, () -> delegated.slowlogGet());
  }

  @Override
  public List<Slowlog> slowlogGet(long entries) {
    String command = "slowlogGet";
    return instrumented(command, () -> delegated.slowlogGet(entries));
  }

  @Override
  public Long objectRefcount(String string) {
    String command = "objectRefcount";
    return instrumented(command, () -> delegated.objectRefcount(string));
  }

  @Override
  public String objectEncoding(String string) {
    String command = "objectEncoding";
    return instrumented(command, () -> delegated.objectEncoding(string));
  }

  @Override
  public Long objectIdletime(String string) {
    String command = "objectIdletime";
    return instrumented(command, () -> delegated.objectIdletime(string));
  }

  @Override
  public Long bitcount(String key) {
    String command = "bitcount";
    return instrumented(command, () -> delegated.bitcount(key));
  }

  @Override
  public Long bitcount(String key, long start, long end) {
    String command = "bitcount";
    return instrumented(command, () -> delegated.bitcount(key, start, end));
  }

  @Override
  public Long bitop(BitOP op, String destKey, String... srcKeys) {
    String command = "bitop";
    return instrumented(command, () -> delegated.bitop(op, destKey, srcKeys));
  }

  @Override
  public List<Map<String, String>> sentinelMasters() {
    String command = "sentinelMasters";
    return instrumented(command, () -> delegated.sentinelMasters());
  }

  @Override
  public List<String> sentinelGetMasterAddrByName(String masterName) {
    String command = "sentinelGetMasterAddrByName";
    return instrumented(command, () -> delegated.sentinelGetMasterAddrByName(masterName));
  }

  @Override
  public Long sentinelReset(String pattern) {
    String command = "sentinelReset";
    return instrumented(command, () -> delegated.sentinelReset(pattern));
  }

  @Override
  public List<Map<String, String>> sentinelSlaves(String masterName) {
    String command = "sentinelSlaves";
    return instrumented(command, () -> delegated.sentinelSlaves(masterName));
  }

  @Override
  public String sentinelFailover(String masterName) {
    String command = "sentinelFailover";
    return instrumented(command, () -> delegated.sentinelFailover(masterName));
  }

  @Override
  public String sentinelMonitor(String masterName, String ip, int port, int quorum) {
    String command = "sentinelMonitor";
    return instrumented(command, () -> delegated.sentinelMonitor(masterName, ip, port, quorum));
  }

  @Override
  public String sentinelRemove(String masterName) {
    String command = "sentinelRemove";
    return instrumented(command, () -> delegated.sentinelRemove(masterName));
  }

  @Override
  public String sentinelSet(String masterName, Map<String, String> parameterMap) {
    String command = "sentinelSet";
    return instrumented(command, () -> delegated.sentinelSet(masterName, parameterMap));
  }

  @Override
  public byte[] dump(String key) {
    String command = "dump";
    return instrumented(command, () -> delegated.dump(key));
  }

  @Override
  public String restore(String key, int ttl, byte[] serializedValue) {
    String command = "restore";
    return instrumented(command, () -> delegated.restore(key, ttl, serializedValue));
  }

  @Override
  public Long pexpire(String key, long milliseconds) {
    String command = "pexpire";
    return instrumented(command, () -> delegated.pexpire(key, milliseconds));
  }

  @Override
  public Long pexpireAt(String key, long millisecondsTimestamp) {
    String command = "pexpireAt";
    return instrumented(command, () -> delegated.pexpireAt(key, millisecondsTimestamp));
  }

  @Override
  public Long pttl(String key) {
    String command = "pttl";
    return instrumented(command, () -> delegated.pttl(key));
  }

  @Override
  public String psetex(String key, long milliseconds, String value) {
    String command = "psetex";
    return instrumented(
        command, payloadSize(value), () -> delegated.psetex(key, milliseconds, value));
  }

  @Override
  public String clientKill(String client) {
    String command = "clientKill";
    return instrumented(command, () -> delegated.clientKill(client));
  }

  @Override
  public String clientSetname(String name) {
    String command = "clientSetname";
    return instrumented(command, () -> delegated.clientSetname(name));
  }

  @Override
  public String migrate(String host, int port, String key, int destinationDb, int timeout) {
    String command = "migrate";
    return instrumented(command, () -> delegated.migrate(host, port, key, destinationDb, timeout));
  }

  @Override
  public ScanResult<String> scan(String cursor) {
    String command = "scan";
    return instrumented(command, () -> delegated.scan(cursor));
  }

  @Override
  public ScanResult<String> scan(String cursor, ScanParams params) {
    String command = "scan";
    return instrumented(command, () -> delegated.scan(cursor, params));
  }

  @Override
  public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor) {
    String command = "hscan";
    return instrumented(command, () -> delegated.hscan(key, cursor));
  }

  @Override
  public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
    String command = "hscan";
    return instrumented(command, () -> delegated.hscan(key, cursor, params));
  }

  @Override
  public ScanResult<String> sscan(String key, String cursor) {
    String command = "sscan";
    return instrumented(command, () -> delegated.sscan(key, cursor));
  }

  @Override
  public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
    String command = "sscan";
    return instrumented(command, () -> delegated.sscan(key, cursor, params));
  }

  @Override
  public ScanResult<Tuple> zscan(String key, String cursor) {
    String command = "zscan";
    return instrumented(command, () -> delegated.zscan(key, cursor));
  }

  @Override
  public ScanResult<Tuple> zscan(String key, String cursor, ScanParams params) {
    String command = "zscan";
    return instrumented(command, () -> delegated.zscan(key, cursor, params));
  }

  @Override
  public String clusterNodes() {
    String command = "clusterNodes";
    return instrumented(command, () -> delegated.clusterNodes());
  }

  @Override
  public String readonly() {
    String command = "readonly";
    return instrumented(command, () -> delegated.readonly());
  }

  @Override
  public String clusterMeet(String ip, int port) {
    String command = "clusterMeet";
    return instrumented(command, () -> delegated.clusterMeet(ip, port));
  }

  @Override
  public String clusterReset(final ClusterReset resetType) {
    String command = "clusterReset";
    return instrumented(command, () -> delegated.clusterReset(resetType));
  }

  @Override
  public String clusterAddSlots(int... slots) {
    String command = "clusterAddSlots";
    return instrumented(command, () -> delegated.clusterAddSlots(slots));
  }

  @Override
  public String clusterDelSlots(int... slots) {
    String command = "clusterDelSlots";
    return instrumented(command, () -> delegated.clusterDelSlots(slots));
  }

  @Override
  public String clusterInfo() {
    String command = "clusterInfo";
    return instrumented(command, () -> delegated.clusterInfo());
  }

  @Override
  public List<String> clusterGetKeysInSlot(int slot, int count) {
    String command = "clusterGetKeysInSlot";
    return instrumented(command, () -> delegated.clusterGetKeysInSlot(slot, count));
  }

  @Override
  public String clusterSetSlotNode(int slot, String nodeId) {
    String command = "clusterSetSlotNode";
    return instrumented(command, () -> delegated.clusterSetSlotNode(slot, nodeId));
  }

  @Override
  public String clusterSetSlotMigrating(int slot, String nodeId) {
    String command = "clusterSetSlotMigrating";
    return instrumented(command, () -> delegated.clusterSetSlotMigrating(slot, nodeId));
  }

  @Override
  public String clusterSetSlotImporting(int slot, String nodeId) {
    String command = "clusterSetSlotImporting";
    return instrumented(command, () -> delegated.clusterSetSlotImporting(slot, nodeId));
  }

  @Override
  public String clusterSetSlotStable(int slot) {
    String command = "clusterSetSlotStable";
    return instrumented(command, () -> delegated.clusterSetSlotStable(slot));
  }

  @Override
  public String clusterForget(String nodeId) {
    String command = "clusterForget";
    return instrumented(command, () -> delegated.clusterForget(nodeId));
  }

  @Override
  public String clusterFlushSlots() {
    String command = "clusterFlushSlots";
    return instrumented(command, () -> delegated.clusterFlushSlots());
  }

  @Override
  public Long clusterKeySlot(String key) {
    String command = "clusterKeySlot";
    return instrumented(command, () -> delegated.clusterKeySlot(key));
  }

  @Override
  public Long clusterCountKeysInSlot(int slot) {
    String command = "clusterCountKeysInSlot";
    return instrumented(command, () -> delegated.clusterCountKeysInSlot(slot));
  }

  @Override
  public String clusterSaveConfig() {
    String command = "clusterSaveConfig";
    return instrumented(command, () -> delegated.clusterSaveConfig());
  }

  @Override
  public String clusterReplicate(String nodeId) {
    String command = "clusterReplicate";
    return instrumented(command, () -> delegated.clusterReplicate(nodeId));
  }

  @Override
  public List<String> clusterSlaves(String nodeId) {
    String command = "clusterSlaves";
    return instrumented(command, () -> delegated.clusterSlaves(nodeId));
  }

  @Override
  public String clusterFailover() {
    String command = "clusterFailover";
    return instrumented(command, () -> delegated.clusterFailover());
  }

  @Override
  public List<Object> clusterSlots() {
    String command = "clusterSlots";
    return instrumented(command, () -> delegated.clusterSlots());
  }

  @Override
  public String asking() {
    String command = "asking";
    return instrumented(command, () -> delegated.asking());
  }

  @Override
  public List<String> pubsubChannels(String pattern) {
    String command = "pubsubChannels";
    return instrumented(command, () -> delegated.pubsubChannels(pattern));
  }

  @Override
  public Long pubsubNumPat() {
    String command = "pubsubNumPat";
    return instrumented(command, () -> delegated.pubsubNumPat());
  }

  @Override
  public Map<String, String> pubsubNumSub(String... channels) {
    String command = "pubsubNumSub";
    return instrumented(command, () -> delegated.pubsubNumSub(channels));
  }

  @Override
  public void close() {
    super.close();
    delegated.close();
  }

  @Override
  public void setDataSource(JedisPoolAbstract jedisPool) {
    delegated.setDataSource(jedisPool);
  }

  @Override
  public Long pfadd(String key, String... elements) {
    String command = "pfadd";
    return instrumented(command, payloadSize(elements), () -> delegated.pfadd(key, elements));
  }

  @Override
  public long pfcount(String key) {
    String command = "pfcount";
    return instrumented(command, () -> delegated.pfcount(key));
  }

  @Override
  public long pfcount(String... keys) {
    String command = "pfcount";
    return instrumented(command, () -> delegated.pfcount(keys));
  }

  @Override
  public String pfmerge(String destkey, String... sourcekeys) {
    String command = "pfmerge";
    return instrumented(command, () -> delegated.pfmerge(destkey, sourcekeys));
  }

  @Override
  public List<String> blpop(int timeout, String key) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(timeout, key));
  }

  @Override
  public List<String> brpop(int timeout, String key) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(timeout, key));
  }

  @Override
  public Long geoadd(String key, double longitude, double latitude, String member) {
    String command = "geoadd";
    return instrumented(
        command, payloadSize(member), () -> delegated.geoadd(key, longitude, latitude, member));
  }

  @Override
  public Long geoadd(String key, Map<String, GeoCoordinate> memberCoordinateMap) {
    String command = "geoadd";
    return instrumented(command, () -> delegated.geoadd(key, memberCoordinateMap));
  }

  @Override
  public Double geodist(String key, String member1, String member2) {
    String command = "geodist";
    return instrumented(command, () -> delegated.geodist(key, member1, member2));
  }

  @Override
  public Double geodist(String key, String member1, String member2, GeoUnit unit) {
    String command = "geodist";
    return instrumented(command, () -> delegated.geodist(key, member1, member2, unit));
  }

  @Override
  public List<String> geohash(String key, String... members) {
    String command = "geohash";
    return instrumented(command, () -> delegated.geohash(key, members));
  }

  @Override
  public List<GeoCoordinate> geopos(String key, String... members) {
    String command = "geopos";
    return instrumented(command, () -> delegated.geopos(key, members));
  }

  @Override
  public List<GeoRadiusResponse> georadius(
      String key, double longitude, double latitude, double radius, GeoUnit unit) {
    String command = "georadius";
    return instrumented(command, () -> delegated.georadius(key, longitude, latitude, radius, unit));
  }

  @Override
  public List<GeoRadiusResponse> georadius(
      String key,
      double longitude,
      double latitude,
      double radius,
      GeoUnit unit,
      GeoRadiusParam param) {
    String command = "georadius";
    return instrumented(
        command, () -> delegated.georadius(key, longitude, latitude, radius, unit, param));
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(
      String key, String member, double radius, GeoUnit unit) {
    String command = "georadiusByMember";
    return instrumented(command, () -> delegated.georadiusByMember(key, member, radius, unit));
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(
      String key, String member, double radius, GeoUnit unit, GeoRadiusParam param) {
    String command = "georadiusByMember";
    return instrumented(
        command, () -> delegated.georadiusByMember(key, member, radius, unit, param));
  }

  @Override
  public List<Long> bitfield(String key, String... arguments) {
    String command = "bitfield";
    return instrumented(command, () -> delegated.bitfield(key, arguments));
  }

  @Override
  public String ping() {
    String command = "ping";
    return instrumented(command, () -> delegated.ping());
  }

  @Override
  public String set(byte[] key, byte[] value) {
    String command = "set";
    return instrumented(command, payloadSize(value), () -> delegated.set(key, value));
  }

  @Override
  public String set(final byte[] key, final byte[] value, final SetParams params) {
    String command = "set";
    return instrumented(command, payloadSize(value), () -> delegated.set(key, value, params));
  }

  @Override
  public byte[] get(byte[] key) {
    String command = "get";
    return instrumented(command, () -> delegated.get(key));
  }

  @Override
  public String quit() {
    String command = "quit";
    return instrumented(command, () -> delegated.quit());
  }

  @Override
  public Long exists(byte[]... keys) {
    String command = "exists";
    return instrumented(command, () -> delegated.exists(keys));
  }

  @Override
  public Boolean exists(byte[] key) {
    String command = "exists";
    return instrumented(command, () -> delegated.exists(key));
  }

  @Override
  public Long del(byte[]... keys) {
    String command = "del";
    return instrumented(command, () -> delegated.del(keys));
  }

  @Override
  public Long del(byte[] key) {
    String command = "del";
    return instrumented(command, () -> delegated.del(key));
  }

  @Override
  public String type(byte[] key) {
    String command = "type";
    return instrumented(command, () -> delegated.type(key));
  }

  @Override
  public String flushDB() {
    String command = "flushDB";
    return instrumented(command, () -> delegated.flushDB());
  }

  @Override
  public Set<byte[]> keys(byte[] pattern) {
    String command = "keys";
    return instrumented(command, () -> delegated.keys(pattern));
  }

  @Override
  public byte[] randomBinaryKey() {
    String command = "randomBinaryKey";
    return instrumented(command, () -> delegated.randomBinaryKey());
  }

  @Override
  public String rename(byte[] oldkey, byte[] newkey) {
    String command = "rename";
    return instrumented(command, () -> delegated.rename(oldkey, newkey));
  }

  @Override
  public Long renamenx(byte[] oldkey, byte[] newkey) {
    String command = "renamenx";
    return instrumented(command, () -> delegated.renamenx(oldkey, newkey));
  }

  @Override
  public Long dbSize() {
    String command = "dbSize";
    return instrumented(command, () -> delegated.dbSize());
  }

  @Override
  public Long expire(byte[] key, int seconds) {
    String command = "expire";
    return instrumented(command, () -> delegated.expire(key, seconds));
  }

  @Override
  public Long expireAt(byte[] key, long unixTime) {
    String command = "expireAt";
    return instrumented(command, () -> delegated.expireAt(key, unixTime));
  }

  @Override
  public Long ttl(byte[] key) {
    String command = "ttl";
    return instrumented(command, () -> delegated.ttl(key));
  }

  @Override
  public String select(int index) {
    String command = "select";
    return instrumented(command, () -> delegated.select(index));
  }

  @Override
  public Long move(byte[] key, int dbIndex) {
    String command = "move";
    return instrumented(command, () -> delegated.move(key, dbIndex));
  }

  @Override
  public String flushAll() {
    String command = "flushAll";
    return instrumented(command, () -> delegated.flushAll());
  }

  @Override
  public byte[] getSet(byte[] key, byte[] value) {
    String command = "getSet";
    return instrumented(command, payloadSize(value), () -> delegated.getSet(key, value));
  }

  @Override
  public List<byte[]> mget(byte[]... keys) {
    String command = "mget";
    return instrumented(command, () -> delegated.mget(keys));
  }

  @Override
  public Long setnx(byte[] key, byte[] value) {
    String command = "setnx";
    return instrumented(command, payloadSize(value), () -> delegated.setnx(key, value));
  }

  @Override
  public String setex(byte[] key, int seconds, byte[] value) {
    String command = "setex";
    return instrumented(command, payloadSize(value), () -> delegated.setex(key, seconds, value));
  }

  @Override
  public String mset(byte[]... keysvalues) {
    String command = "mset";
    return instrumented(command, payloadSize(keysvalues), () -> delegated.mset(keysvalues));
  }

  @Override
  public Long msetnx(byte[]... keysvalues) {
    String command = "msetnx";
    return instrumented(command, payloadSize(keysvalues), () -> delegated.msetnx(keysvalues));
  }

  @Override
  public Long decrBy(byte[] key, long integer) {
    String command = "decrBy";
    return instrumented(command, () -> delegated.decrBy(key, integer));
  }

  @Override
  public Long decr(byte[] key) {
    String command = "decr";
    return instrumented(command, () -> delegated.decr(key));
  }

  @Override
  public Long incrBy(byte[] key, long integer) {
    String command = "incrBy";
    return instrumented(command, () -> delegated.incrBy(key, integer));
  }

  @Override
  public Double incrByFloat(byte[] key, double integer) {
    String command = "incrByFloat";
    return instrumented(command, () -> delegated.incrByFloat(key, integer));
  }

  @Override
  public Long incr(byte[] key) {
    String command = "incr";
    return instrumented(command, () -> delegated.incr(key));
  }

  @Override
  public Long append(byte[] key, byte[] value) {
    String command = "append";
    return instrumented(command, payloadSize(value), () -> delegated.append(key, value));
  }

  @Override
  public byte[] substr(byte[] key, int start, int end) {
    String command = "substr";
    return instrumented(command, () -> delegated.substr(key, start, end));
  }

  @Override
  public Long hset(byte[] key, byte[] field, byte[] value) {
    String command = "hset";
    return instrumented(command, payloadSize(value), () -> delegated.hset(key, field, value));
  }

  @Override
  public byte[] hget(byte[] key, byte[] field) {
    String command = "hget";
    return instrumented(command, () -> delegated.hget(key, field));
  }

  @Override
  public Long hsetnx(byte[] key, byte[] field, byte[] value) {
    String command = "hsetnx";
    return instrumented(command, payloadSize(value), () -> delegated.hsetnx(key, field, value));
  }

  @Override
  public String hmset(byte[] key, Map<byte[], byte[]> hash) {
    String command = "hmset";
    return instrumented(command, () -> delegated.hmset(key, hash));
  }

  @Override
  public List<byte[]> hmget(byte[] key, byte[]... fields) {
    String command = "hmget";
    return instrumented(command, () -> delegated.hmget(key, fields));
  }

  @Override
  public Long hincrBy(byte[] key, byte[] field, long value) {
    String command = "hincrBy";
    return instrumented(command, () -> delegated.hincrBy(key, field, value));
  }

  @Override
  public Double hincrByFloat(byte[] key, byte[] field, double value) {
    String command = "hincrByFloat";
    return instrumented(command, () -> delegated.hincrByFloat(key, field, value));
  }

  @Override
  public Boolean hexists(byte[] key, byte[] field) {
    String command = "hexists";
    return instrumented(command, () -> delegated.hexists(key, field));
  }

  @Override
  public Long hdel(byte[] key, byte[]... fields) {
    String command = "hdel";
    return instrumented(command, () -> delegated.hdel(key, fields));
  }

  @Override
  public Long hlen(byte[] key) {
    String command = "hlen";
    return instrumented(command, () -> delegated.hlen(key));
  }

  @Override
  public Set<byte[]> hkeys(byte[] key) {
    String command = "hkeys";
    return instrumented(command, () -> delegated.hkeys(key));
  }

  @Override
  public List<byte[]> hvals(byte[] key) {
    String command = "hvals";
    return instrumented(command, () -> delegated.hvals(key));
  }

  @Override
  public Map<byte[], byte[]> hgetAll(byte[] key) {
    String command = "hgetAll";
    return instrumented(command, () -> delegated.hgetAll(key));
  }

  @Override
  public Long rpush(byte[] key, byte[]... strings) {
    String command = "rpush";
    return instrumented(command, payloadSize(strings), () -> delegated.rpush(key, strings));
  }

  @Override
  public Long lpush(byte[] key, byte[]... strings) {
    String command = "lpush";
    return instrumented(command, payloadSize(strings), () -> delegated.lpush(key, strings));
  }

  @Override
  public Long llen(byte[] key) {
    String command = "llen";
    return instrumented(command, () -> delegated.llen(key));
  }

  @Override
  public List<byte[]> lrange(byte[] key, long start, long end) {
    String command = "lrange";
    return instrumented(command, () -> delegated.lrange(key, start, end));
  }

  @Override
  public String ltrim(byte[] key, long start, long end) {
    String command = "ltrim";
    return instrumented(command, () -> delegated.ltrim(key, start, end));
  }

  @Override
  public byte[] lindex(byte[] key, long index) {
    String command = "lindex";
    return instrumented(command, () -> delegated.lindex(key, index));
  }

  @Override
  public String lset(byte[] key, long index, byte[] value) {
    String command = "lset";
    return instrumented(command, payloadSize(value), () -> delegated.lset(key, index, value));
  }

  @Override
  public Long lrem(byte[] key, long count, byte[] value) {
    String command = "lrem";
    return instrumented(command, payloadSize(value), () -> delegated.lrem(key, count, value));
  }

  @Override
  public byte[] lpop(byte[] key) {
    String command = "lpop";
    return instrumented(command, () -> delegated.lpop(key));
  }

  @Override
  public byte[] rpop(byte[] key) {
    String command = "rpop";
    return instrumented(command, () -> delegated.rpop(key));
  }

  @Override
  public byte[] rpoplpush(byte[] srckey, byte[] dstkey) {
    String command = "rpoplpush";
    return instrumented(command, () -> delegated.rpoplpush(srckey, dstkey));
  }

  @Override
  public Long sadd(byte[] key, byte[]... members) {
    String command = "sadd";
    return instrumented(command, payloadSize(members), () -> delegated.sadd(key, members));
  }

  @Override
  public Set<byte[]> smembers(byte[] key) {
    String command = "smembers";
    return instrumented(command, () -> delegated.smembers(key));
  }

  @Override
  public Long srem(byte[] key, byte[]... member) {
    String command = "srem";
    return instrumented(command, payloadSize(member), () -> delegated.srem(key, member));
  }

  @Override
  public byte[] spop(byte[] key) {
    String command = "spop";
    return instrumented(command, () -> delegated.spop(key));
  }

  @Override
  public Set<byte[]> spop(byte[] key, long count) {
    String command = "spop";
    return instrumented(command, () -> delegated.spop(key, count));
  }

  @Override
  public Long smove(byte[] srckey, byte[] dstkey, byte[] member) {
    String command = "smove";
    return instrumented(command, () -> delegated.smove(srckey, dstkey, member));
  }

  @Override
  public Long scard(byte[] key) {
    String command = "scard";
    return instrumented(command, () -> delegated.scard(key));
  }

  @Override
  public Boolean sismember(byte[] key, byte[] member) {
    String command = "sismember";
    return instrumented(command, () -> delegated.sismember(key, member));
  }

  @Override
  public Set<byte[]> sinter(byte[]... keys) {
    String command = "sinter";
    return instrumented(command, () -> delegated.sinter(keys));
  }

  @Override
  public Long sinterstore(byte[] dstkey, byte[]... keys) {
    String command = "sinterstore";
    return instrumented(command, () -> delegated.sinterstore(dstkey, keys));
  }

  @Override
  public Set<byte[]> sunion(byte[]... keys) {
    String command = "sunion";
    return instrumented(command, () -> delegated.sunion(keys));
  }

  @Override
  public Long sunionstore(byte[] dstkey, byte[]... keys) {
    String command = "sunionstore";
    return instrumented(command, () -> delegated.sunionstore(dstkey, keys));
  }

  @Override
  public Set<byte[]> sdiff(byte[]... keys) {
    String command = "sdiff";
    return instrumented(command, () -> delegated.sdiff(keys));
  }

  @Override
  public Long sdiffstore(byte[] dstkey, byte[]... keys) {
    String command = "sdiffstore";
    return instrumented(command, () -> delegated.sdiffstore(dstkey, keys));
  }

  @Override
  public byte[] srandmember(byte[] key) {
    String command = "srandmember";
    return instrumented(command, () -> delegated.srandmember(key));
  }

  @Override
  public List<byte[]> srandmember(byte[] key, int count) {
    String command = "srandmember";
    return instrumented(command, () -> delegated.srandmember(key, count));
  }

  @Override
  public Long zadd(byte[] key, double score, byte[] member) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, score, member));
  }

  @Override
  public Long zadd(byte[] key, double score, byte[] member, ZAddParams params) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, score, member, params));
  }

  @Override
  public Long zadd(byte[] key, Map<byte[], Double> scoreMembers) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, scoreMembers));
  }

  @Override
  public Long zadd(byte[] key, Map<byte[], Double> scoreMembers, ZAddParams params) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, scoreMembers, params));
  }

  @Override
  public Set<byte[]> zrange(byte[] key, long start, long end) {
    String command = "zrange";
    return instrumented(command, () -> delegated.zrange(key, start, end));
  }

  @Override
  public Long zrem(byte[] key, byte[]... members) {
    String command = "zrem";
    return instrumented(command, () -> delegated.zrem(key, members));
  }

  @Override
  public Double zincrby(byte[] key, double score, byte[] member) {
    String command = "zincrby";
    return instrumented(command, () -> delegated.zincrby(key, score, member));
  }

  @Override
  public Double zincrby(byte[] key, double score, byte[] member, ZIncrByParams params) {
    String command = "zincrby";
    return instrumented(command, () -> delegated.zincrby(key, score, member, params));
  }

  @Override
  public Long zrank(byte[] key, byte[] member) {
    String command = "zrank";
    return instrumented(command, () -> delegated.zrank(key, member));
  }

  @Override
  public Long zrevrank(byte[] key, byte[] member) {
    String command = "zrevrank";
    return instrumented(command, () -> delegated.zrevrank(key, member));
  }

  @Override
  public Set<byte[]> zrevrange(byte[] key, long start, long end) {
    String command = "zrevrange";
    return instrumented(command, () -> delegated.zrevrange(key, start, end));
  }

  @Override
  public Set<Tuple> zrangeWithScores(byte[] key, long start, long end) {
    String command = "zrangeWithScores";
    return instrumented(command, () -> delegated.zrangeWithScores(key, start, end));
  }

  @Override
  public Set<Tuple> zrevrangeWithScores(byte[] key, long start, long end) {
    String command = "zrevrangeWithScores";
    return instrumented(command, () -> delegated.zrevrangeWithScores(key, start, end));
  }

  @Override
  public Long zcard(byte[] key) {
    String command = "zcard";
    return instrumented(command, () -> delegated.zcard(key));
  }

  @Override
  public Double zscore(byte[] key, byte[] member) {
    String command = "zscore";
    return instrumented(command, () -> delegated.zscore(key, member));
  }

  @Override
  public Transaction multi() {
    String command = "multi";
    return instrumented(command, () -> delegated.multi());
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
  public String watch(byte[]... keys) {
    String command = "watch";
    return instrumented(command, () -> delegated.watch(keys));
  }

  @Override
  public String unwatch() {
    String command = "unwatch";
    return instrumented(command, () -> delegated.unwatch());
  }

  @Override
  public List<byte[]> sort(byte[] key) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key));
  }

  @Override
  public List<byte[]> sort(byte[] key, SortingParams sortingParameters) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, sortingParameters));
  }

  @Override
  public List<byte[]> blpop(int timeout, byte[]... keys) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(timeout, keys));
  }

  @Override
  public Long sort(byte[] key, SortingParams sortingParameters, byte[] dstkey) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, sortingParameters, dstkey));
  }

  @Override
  public Long sort(byte[] key, byte[] dstkey) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, dstkey));
  }

  @Override
  public List<byte[]> brpop(int timeout, byte[]... keys) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(timeout, keys));
  }

  @Override
  public List<byte[]> blpop(byte[]... args) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(args));
  }

  @Override
  public List<byte[]> brpop(byte[]... args) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(args));
  }

  @Override
  public String auth(String password) {
    String command = "auth";
    return instrumented(command, () -> delegated.auth(password));
  }

  @Override
  public Pipeline pipelined() {
    String command = "pipelined";
    return instrumented(
        command, () -> new InstrumentedPipeline(registry, delegated.pipelined(), poolName));
  }

  @Override
  public Long zcount(byte[] key, double min, double max) {
    String command = "zcount";
    return instrumented(command, () -> delegated.zcount(key, min, max));
  }

  @Override
  public Long zcount(byte[] key, byte[] min, byte[] max) {
    String command = "zcount";
    return instrumented(command, () -> delegated.zcount(key, min, max));
  }

  @Override
  public Set<byte[]> zrangeByScore(byte[] key, double min, double max) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max));
  }

  @Override
  public Set<byte[]> zrangeByScore(byte[] key, byte[] min, byte[] max) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max));
  }

  @Override
  public Set<byte[]> zrangeByScore(byte[] key, double min, double max, int offset, int count) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Set<byte[]> zrangeByScore(byte[] key, byte[] min, byte[] max, int offset, int count) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(byte[] key, double min, double max) {
    String command = "zrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max) {
    String command = "zrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(
      byte[] key, double min, double max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(
      byte[] key, byte[] min, byte[] max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Set<byte[]> zrevrangeByScore(byte[] key, double max, double min) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min));
  }

  @Override
  public Set<byte[]> zrevrangeByScore(byte[] key, byte[] max, byte[] min) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min));
  }

  @Override
  public Set<byte[]> zrevrangeByScore(byte[] key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Set<byte[]> zrevrangeByScore(byte[] key, byte[] max, byte[] min, int offset, int count) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, double max, double min) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(
      byte[] key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(
      byte[] key, byte[] max, byte[] min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Long zremrangeByRank(byte[] key, long start, long end) {
    String command = "zremrangeByRank";
    return instrumented(command, () -> delegated.zremrangeByRank(key, start, end));
  }

  @Override
  public Long zremrangeByScore(byte[] key, double start, double end) {
    String command = "zremrangeByScore";
    return instrumented(command, () -> delegated.zremrangeByScore(key, start, end));
  }

  @Override
  public Long zremrangeByScore(byte[] key, byte[] start, byte[] end) {
    String command = "zremrangeByScore";
    return instrumented(command, () -> delegated.zremrangeByScore(key, start, end));
  }

  @Override
  public Long zunionstore(byte[] dstkey, byte[]... sets) {
    String command = "zunionstore";
    return instrumented(command, () -> delegated.zunionstore(dstkey, sets));
  }

  @Override
  public Long zunionstore(byte[] dstkey, ZParams params, byte[]... sets) {
    String command = "zunionstore";
    return instrumented(command, () -> delegated.zunionstore(dstkey, params, sets));
  }

  @Override
  public Long zinterstore(byte[] dstkey, byte[]... sets) {
    String command = "zinterstore";
    return instrumented(command, () -> delegated.zinterstore(dstkey, sets));
  }

  @Override
  public Long zinterstore(byte[] dstkey, ZParams params, byte[]... sets) {
    String command = "zinterstore";
    return instrumented(command, () -> delegated.zinterstore(dstkey, params, sets));
  }

  @Override
  public Long zlexcount(byte[] key, byte[] min, byte[] max) {
    String command = "zlexcount";
    return instrumented(command, () -> delegated.zlexcount(key, min, max));
  }

  @Override
  public Set<byte[]> zrangeByLex(byte[] key, byte[] min, byte[] max) {
    String command = "zrangeByLex";
    return instrumented(command, () -> delegated.zrangeByLex(key, min, max));
  }

  @Override
  public Set<byte[]> zrangeByLex(byte[] key, byte[] min, byte[] max, int offset, int count) {
    String command = "zrangeByLex";
    return instrumented(command, () -> delegated.zrangeByLex(key, min, max, offset, count));
  }

  @Override
  public Set<byte[]> zrevrangeByLex(byte[] key, byte[] max, byte[] min) {
    String command = "zrevrangeByLex";
    return instrumented(command, () -> delegated.zrevrangeByLex(key, max, min));
  }

  @Override
  public Set<byte[]> zrevrangeByLex(byte[] key, byte[] max, byte[] min, int offset, int count) {
    String command = "zrevrangeByLex";
    return instrumented(command, () -> delegated.zrevrangeByLex(key, max, min, offset, count));
  }

  @Override
  public Long zremrangeByLex(byte[] key, byte[] min, byte[] max) {
    String command = "zremrangeByLex";
    return instrumented(command, () -> delegated.zremrangeByLex(key, min, max));
  }

  @Override
  public String save() {
    String command = "save";
    return instrumented(command, () -> delegated.save());
  }

  @Override
  public String bgsave() {
    String command = "bgsave";
    return instrumented(command, () -> delegated.bgsave());
  }

  @Override
  public String bgrewriteaof() {
    String command = "bgrewriteaof";
    return instrumented(command, () -> delegated.bgrewriteaof());
  }

  @Override
  public Long lastsave() {
    String command = "lastsave";
    return instrumented(command, () -> delegated.lastsave());
  }

  @Override
  public String shutdown() {
    String command = "shutdown";
    return instrumented(command, () -> delegated.shutdown());
  }

  @Override
  public String info() {
    String command = "info";
    return instrumented(command, () -> delegated.info());
  }

  @Override
  public String info(String section) {
    String command = "info";
    return instrumented(command, () -> delegated.info(section));
  }

  @Override
  public void monitor(JedisMonitor jedisMonitor) {
    delegated.monitor(jedisMonitor);
  }

  @Override
  public String slaveof(String host, int port) {
    String command = "slaveof";
    return instrumented(command, () -> delegated.slaveof(host, port));
  }

  @Override
  public String slaveofNoOne() {
    String command = "slaveofNoOne";
    return instrumented(command, () -> delegated.slaveofNoOne());
  }

  @Override
  public List<byte[]> configGet(byte[] pattern) {
    String command = "configGet";
    return instrumented(command, () -> delegated.configGet(pattern));
  }

  @Override
  public String configResetStat() {
    String command = "configResetStat";
    return instrumented(command, () -> delegated.configResetStat());
  }

  @Override
  public byte[] configSet(byte[] parameter, byte[] value) {
    String command = "configSet";
    return instrumented(command, () -> delegated.configSet(parameter, value));
  }

  @Override
  public boolean isConnected() {
    String command = "isConnected";
    return instrumented(command, () -> delegated.isConnected());
  }

  @Override
  public Long strlen(byte[] key) {
    String command = "strlen";
    return instrumented(command, () -> delegated.strlen(key));
  }

  @Override
  public void sync() {
    delegated.sync();
  }

  @Override
  public Long lpushx(byte[] key, byte[]... string) {
    String command = "lpushx";
    return instrumented(command, payloadSize(string), () -> delegated.lpushx(key, string));
  }

  @Override
  public Long persist(byte[] key) {
    String command = "persist";
    return instrumented(command, () -> delegated.persist(key));
  }

  @Override
  public Long rpushx(byte[] key, byte[]... string) {
    String command = "rpushx";
    return instrumented(command, payloadSize(string), () -> delegated.rpushx(key, string));
  }

  @Override
  public byte[] echo(byte[] string) {
    String command = "echo";
    return instrumented(command, () -> delegated.echo(string));
  }

  @Override
  public Long linsert(
      final byte[] key, final ListPosition where, final byte[] pivot, final byte[] value) {
    String command = "linsert";
    return instrumented(
        command, payloadSize(value), () -> delegated.linsert(key, where, pivot, value));
  }

  @Override
  public String debug(DebugParams params) {
    String command = "debug";
    return instrumented(command, () -> delegated.debug(params));
  }

  @Override
  public Client getClient() {
    String command = "getClient";
    return instrumented(command, () -> delegated.getClient());
  }

  @Override
  public byte[] brpoplpush(byte[] source, byte[] destination, int timeout) {
    String command = "brpoplpush";
    return instrumented(command, () -> delegated.brpoplpush(source, destination, timeout));
  }

  @Override
  public Boolean setbit(byte[] key, long offset, boolean value) {
    String command = "setbit";
    return instrumented(command, () -> delegated.setbit(key, offset, value));
  }

  @Override
  public Boolean setbit(byte[] key, long offset, byte[] value) {
    String command = "setbit";
    return instrumented(command, payloadSize(value), () -> delegated.setbit(key, offset, value));
  }

  @Override
  public Boolean getbit(byte[] key, long offset) {
    String command = "getbit";
    return instrumented(command, () -> delegated.getbit(key, offset));
  }

  @Override
  public Long bitpos(byte[] key, boolean value) {
    String command = "bitpos";
    return instrumented(command, () -> delegated.bitpos(key, value));
  }

  @Override
  public Long bitpos(byte[] key, boolean value, BitPosParams params) {
    String command = "bitpos";
    return instrumented(command, () -> delegated.bitpos(key, value, params));
  }

  @Override
  public Long setrange(byte[] key, long offset, byte[] value) {
    String command = "setrange";
    return instrumented(command, payloadSize(value), () -> delegated.setrange(key, offset, value));
  }

  @Override
  public byte[] getrange(byte[] key, long startOffset, long endOffset) {
    String command = "getrange";
    return instrumented(command, () -> delegated.getrange(key, startOffset, endOffset));
  }

  @Override
  public Long publish(byte[] channel, byte[] message) {
    String command = "publish";
    return instrumented(command, payloadSize(message), () -> delegated.publish(channel, message));
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
  public int getDB() {
    String command = "getDB";
    return instrumented(command, () -> delegated.getDB());
  }

  @Override
  public Object eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
    String command = "eval";
    return instrumented(
        command, payloadSize(script) + payloadSize(args), () -> delegated.eval(script, keys, args));
  }

  @Override
  public Object eval(byte[] script, byte[] keyCount, byte[]... params) {
    String command = "eval";
    return instrumented(
        command,
        payloadSize(script) + payloadSize(params),
        () -> delegated.eval(script, keyCount, params));
  }

  @Override
  public Object eval(byte[] script, int keyCount, byte[]... params) {
    String command = "eval";
    return instrumented(
        command,
        payloadSize(script) + payloadSize(params),
        () -> delegated.eval(script, keyCount, params));
  }

  @Override
  public Object eval(byte[] script) {
    String command = "eval";
    return instrumented(command, payloadSize(script), () -> delegated.eval(script));
  }

  @Override
  public Object evalsha(byte[] sha1) {
    String command = "evalsha";
    return instrumented(command, () -> delegated.evalsha(sha1));
  }

  @Override
  public Object evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
    String command = "evalsha";
    return instrumented(command, payloadSize(args), () -> delegated.evalsha(sha1, keys, args));
  }

  @Override
  public Object evalsha(byte[] sha1, int keyCount, byte[]... params) {
    String command = "evalsha";
    return instrumented(
        command, payloadSize(params), () -> delegated.evalsha(sha1, keyCount, params));
  }

  @Override
  public String scriptFlush() {
    String command = "scriptFlush";
    return instrumented(command, () -> delegated.scriptFlush());
  }

  @Override
  public Long scriptExists(byte[] sha1) {
    String command = "scriptExists";
    return instrumented(command, () -> delegated.scriptExists(sha1));
  }

  @Override
  public List<Long> scriptExists(byte[]... sha1) {
    String command = "scriptExists";
    return instrumented(command, () -> delegated.scriptExists(sha1));
  }

  @Override
  public byte[] scriptLoad(byte[] script) {
    String command = "scriptLoad";
    return instrumented(command, payloadSize(script), () -> delegated.scriptLoad(script));
  }

  @Override
  public String scriptKill() {
    String command = "scriptKill";
    return instrumented(command, () -> delegated.scriptKill());
  }

  @Override
  public String slowlogReset() {
    String command = "slowlogReset";
    return instrumented(command, () -> delegated.slowlogReset());
  }

  @Override
  public Long slowlogLen() {
    String command = "slowlogLen";
    return instrumented(command, () -> delegated.slowlogLen());
  }

  @Override
  public List<byte[]> slowlogGetBinary() {
    String command = "slowlogGetBinary";
    return instrumented(command, () -> delegated.slowlogGetBinary());
  }

  @Override
  public List<byte[]> slowlogGetBinary(long entries) {
    String command = "slowlogGetBinary";
    return instrumented(command, () -> delegated.slowlogGetBinary(entries));
  }

  @Override
  public Long objectRefcount(byte[] key) {
    String command = "objectRefcount";
    return instrumented(command, () -> delegated.objectRefcount(key));
  }

  @Override
  public byte[] objectEncoding(byte[] key) {
    String command = "objectEncoding";
    return instrumented(command, () -> delegated.objectEncoding(key));
  }

  @Override
  public Long objectIdletime(byte[] key) {
    String command = "objectIdletime";
    return instrumented(command, () -> delegated.objectIdletime(key));
  }

  @Override
  public Long bitcount(byte[] key) {
    String command = "bitcount";
    return instrumented(command, () -> delegated.bitcount(key));
  }

  @Override
  public Long bitcount(byte[] key, long start, long end) {
    String command = "bitcount";
    return instrumented(command, () -> delegated.bitcount(key, start, end));
  }

  @Override
  public Long bitop(BitOP op, byte[] destKey, byte[]... srcKeys) {
    String command = "bitop";
    return instrumented(command, () -> delegated.bitop(op, destKey, srcKeys));
  }

  @Override
  public byte[] dump(byte[] key) {
    String command = "dump";
    return instrumented(command, () -> delegated.dump(key));
  }

  @Override
  public String restore(byte[] key, int ttl, byte[] serializedValue) {
    String command = "restore";
    return instrumented(command, () -> delegated.restore(key, ttl, serializedValue));
  }

  @Override
  public Long pexpire(byte[] key, long milliseconds) {
    String command = "pexpire";
    return instrumented(command, () -> delegated.pexpire(key, milliseconds));
  }

  @Override
  public Long pexpireAt(byte[] key, long millisecondsTimestamp) {
    String command = "pexpireAt";
    return instrumented(command, () -> delegated.pexpireAt(key, millisecondsTimestamp));
  }

  @Override
  public Long pttl(byte[] key) {
    String command = "pttl";
    return instrumented(command, () -> delegated.pttl(key));
  }

  @Override
  public String psetex(byte[] key, long milliseconds, byte[] value) {
    String command = "psetex";
    return instrumented(
        command, payloadSize(value), () -> delegated.psetex(key, milliseconds, value));
  }

  @Override
  public String clientKill(byte[] client) {
    String command = "clientKill";
    return instrumented(command, () -> delegated.clientKill(client));
  }

  @Override
  public String clientGetname() {
    String command = "clientGetname";
    return instrumented(command, () -> delegated.clientGetname());
  }

  @Override
  public String clientList() {
    String command = "clientList";
    return instrumented(command, () -> delegated.clientList());
  }

  @Override
  public String clientSetname(byte[] name) {
    String command = "clientSetname";
    return instrumented(command, () -> delegated.clientSetname(name));
  }

  @Override
  public List<String> time() {
    String command = "time";
    return instrumented(command, () -> delegated.time());
  }

  @Override
  public Long waitReplicas(int replicas, long timeout) {
    String command = "waitReplicas";
    return instrumented(command, () -> delegated.waitReplicas(replicas, timeout));
  }

  @Override
  public Long pfadd(byte[] key, byte[]... elements) {
    String command = "pfadd";
    return instrumented(command, payloadSize(elements), () -> delegated.pfadd(key, elements));
  }

  @Override
  public long pfcount(byte[] key) {
    String command = "pfcount";
    return instrumented(command, () -> delegated.pfcount(key));
  }

  @Override
  public String pfmerge(byte[] destkey, byte[]... sourcekeys) {
    String command = "pfmerge";
    return instrumented(command, () -> delegated.pfmerge(destkey, sourcekeys));
  }

  @Override
  public Long pfcount(byte[]... keys) {
    String command = "pfcount";
    return instrumented(command, () -> delegated.pfcount(keys));
  }

  @Override
  public ScanResult<byte[]> scan(byte[] cursor) {
    String command = "scan";
    return instrumented(command, () -> delegated.scan(cursor));
  }

  @Override
  public ScanResult<byte[]> scan(byte[] cursor, ScanParams params) {
    String command = "scan";
    return instrumented(command, () -> delegated.scan(cursor, params));
  }

  @Override
  public ScanResult<Map.Entry<byte[], byte[]>> hscan(byte[] key, byte[] cursor) {
    String command = "hscan";
    return instrumented(command, () -> delegated.hscan(key, cursor));
  }

  @Override
  public ScanResult<Map.Entry<byte[], byte[]>> hscan(byte[] key, byte[] cursor, ScanParams params) {
    String command = "hscan";
    return instrumented(command, () -> delegated.hscan(key, cursor, params));
  }

  @Override
  public ScanResult<byte[]> sscan(byte[] key, byte[] cursor) {
    String command = "sscan";
    return instrumented(command, () -> delegated.sscan(key, cursor));
  }

  @Override
  public ScanResult<byte[]> sscan(byte[] key, byte[] cursor, ScanParams params) {
    String command = "sscan";
    return instrumented(command, () -> delegated.sscan(key, cursor, params));
  }

  @Override
  public ScanResult<Tuple> zscan(byte[] key, byte[] cursor) {
    String command = "zscan";
    return instrumented(command, () -> delegated.zscan(key, cursor));
  }

  @Override
  public ScanResult<Tuple> zscan(byte[] key, byte[] cursor, ScanParams params) {
    String command = "zscan";
    return instrumented(command, () -> delegated.zscan(key, cursor, params));
  }

  @Override
  public Long geoadd(byte[] key, double longitude, double latitude, byte[] member) {
    String command = "geoadd";
    return instrumented(command, () -> delegated.geoadd(key, longitude, latitude, member));
  }

  @Override
  public Long geoadd(byte[] key, Map<byte[], GeoCoordinate> memberCoordinateMap) {
    String command = "geoadd";
    return instrumented(command, () -> delegated.geoadd(key, memberCoordinateMap));
  }

  @Override
  public Double geodist(byte[] key, byte[] member1, byte[] member2) {
    String command = "geodist";
    return instrumented(command, () -> delegated.geodist(key, member1, member2));
  }

  @Override
  public Double geodist(byte[] key, byte[] member1, byte[] member2, GeoUnit unit) {
    String command = "geodist";
    return instrumented(command, () -> delegated.geodist(key, member1, member2, unit));
  }

  @Override
  public List<byte[]> geohash(byte[] key, byte[]... members) {
    String command = "geohash";
    return instrumented(command, () -> delegated.geohash(key, members));
  }

  @Override
  public List<GeoCoordinate> geopos(byte[] key, byte[]... members) {
    String command = "geopos";
    return instrumented(command, () -> delegated.geopos(key, members));
  }

  @Override
  public List<GeoRadiusResponse> georadius(
      byte[] key, double longitude, double latitude, double radius, GeoUnit unit) {
    String command = "georadius";
    return instrumented(command, () -> delegated.georadius(key, longitude, latitude, radius, unit));
  }

  @Override
  public List<GeoRadiusResponse> georadius(
      byte[] key,
      double longitude,
      double latitude,
      double radius,
      GeoUnit unit,
      GeoRadiusParam param) {
    String command = "georadius";
    return instrumented(
        command, () -> delegated.georadius(key, longitude, latitude, radius, unit, param));
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(
      byte[] key, byte[] member, double radius, GeoUnit unit) {
    String command = "georadiusByMember";
    return instrumented(command, () -> delegated.georadiusByMember(key, member, radius, unit));
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(
      byte[] key, byte[] member, double radius, GeoUnit unit, GeoRadiusParam param) {
    String command = "georadiusByMember";
    return instrumented(
        command, () -> delegated.georadiusByMember(key, member, radius, unit, param));
  }

  @Override
  public List<Long> bitfield(byte[] key, byte[]... arguments) {
    String command = "bitfield";
    return instrumented(command, () -> delegated.bitfield(key, arguments));
  }
}

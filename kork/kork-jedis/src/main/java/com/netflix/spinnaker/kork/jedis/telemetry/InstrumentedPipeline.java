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

public class InstrumentedPipeline extends Pipeline {

  private final Registry registry;
  private final Pipeline delegated;
  private final String poolName;

  public InstrumentedPipeline(Registry registry, Pipeline delegated) {
    this(registry, delegated, "unnamed");
  }

  public InstrumentedPipeline(Registry registry, Pipeline delegated, String poolName) {
    this.registry = registry;
    this.delegated = delegated;
    this.poolName = poolName;
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
                    registry, payloadSizeId(registry, poolName, command, true))
                .record(size));
    try {
      return PercentileTimer.get(registry, timerId(registry, poolName, command, true))
          .record(
              () -> {
                T result = action.call();
                registry.counter(invocationId(registry, poolName, command, true, true)).increment();
                return result;
              });
    } catch (Exception e) {
      registry.counter(invocationId(registry, poolName, command, true, false)).increment();
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
                    registry, payloadSizeId(registry, poolName, command, true))
                .record(size));
    try {
      PercentileTimer.get(registry, timerId(registry, poolName, command, true))
          .record(
              () -> {
                action.run();
                registry.counter(invocationId(registry, poolName, command, true, true)).increment();
              });
    } catch (Exception e) {
      registry.counter(invocationId(registry, poolName, command, true, false)).increment();
      throw new InstrumentedJedisException("could not execute delegate function", e);
    }
  }

  @Override
  public void setClient(Client client) {
    delegated.setClient(client);
  }

  @Override
  public void clear() {
    delegated.clear();
  }

  @Override
  public boolean isInMulti() {
    String command = "isInMulti";
    return instrumented(command, () -> delegated.isInMulti());
  }

  @Override
  public void sync() {
    delegated.sync();
  }

  @Override
  public List<Object> syncAndReturnAll() {
    String command = "syncAndReturnAll";
    return instrumented(command, () -> delegated.syncAndReturnAll());
  }

  @Override
  public Response<String> discard() {
    String command = "discard";
    return instrumented(command, () -> delegated.discard());
  }

  @Override
  public Response<List<Object>> exec() {
    String command = "exec";
    return instrumented(command, () -> delegated.exec());
  }

  @Override
  public Response<String> multi() {
    String command = "multi";
    return instrumented(command, () -> delegated.multi());
  }

  @Override
  public void close() {
    super.close();
    delegated.close();
  }

  @Override
  public Response<List<String>> brpop(String... args) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(args));
  }

  @Override
  public Response<List<String>> brpop(int timeout, String... keys) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(timeout, keys));
  }

  @Override
  public Response<List<String>> blpop(String... args) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(args));
  }

  @Override
  public Response<List<String>> blpop(int timeout, String... keys) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(timeout, keys));
  }

  @Override
  public Response<Map<String, String>> blpopMap(int timeout, String... keys) {
    String command = "blpopMap";
    return instrumented(command, () -> delegated.blpopMap(timeout, keys));
  }

  @Override
  public Response<List<byte[]>> brpop(byte[]... args) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(args));
  }

  @Override
  public Response<List<String>> brpop(int timeout, byte[]... keys) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(timeout, keys));
  }

  @Override
  public Response<Map<String, String>> brpopMap(int timeout, String... keys) {
    String command = "brpopMap";
    return instrumented(command, () -> delegated.brpopMap(timeout, keys));
  }

  @Override
  public Response<List<byte[]>> blpop(byte[]... args) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(args));
  }

  @Override
  public Response<List<String>> blpop(int timeout, byte[]... keys) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(timeout, keys));
  }

  @Override
  public Response<Long> del(String... keys) {
    String command = "del";
    return instrumented(command, () -> delegated.del(keys));
  }

  @Override
  public Response<Long> del(byte[]... keys) {
    String command = "del";
    return instrumented(command, () -> delegated.del(keys));
  }

  @Override
  public Response<Long> exists(String... keys) {
    String command = "exists";
    return instrumented(command, () -> delegated.exists(keys));
  }

  @Override
  public Response<Long> exists(byte[]... keys) {
    String command = "exists";
    return instrumented(command, () -> delegated.exists(keys));
  }

  @Override
  public Response<Set<String>> keys(String pattern) {
    String command = "keys";
    return instrumented(command, () -> delegated.keys(pattern));
  }

  @Override
  public Response<Set<byte[]>> keys(byte[] pattern) {
    String command = "keys";
    return instrumented(command, () -> delegated.keys(pattern));
  }

  @Override
  public Response<List<String>> mget(String... keys) {
    String command = "mget";
    return instrumented(command, () -> delegated.mget(keys));
  }

  @Override
  public Response<List<byte[]>> mget(byte[]... keys) {
    String command = "mget";
    return instrumented(command, () -> delegated.mget(keys));
  }

  @Override
  public Response<String> mset(String... keysvalues) {
    String command = "mset";
    return instrumented(command, payloadSize(keysvalues), () -> delegated.mset(keysvalues));
  }

  @Override
  public Response<String> mset(byte[]... keysvalues) {
    String command = "mset";
    return instrumented(command, payloadSize(keysvalues), () -> delegated.mset(keysvalues));
  }

  @Override
  public Response<Long> msetnx(String... keysvalues) {
    String command = "msetnx";
    return instrumented(command, payloadSize(keysvalues), () -> delegated.msetnx(keysvalues));
  }

  @Override
  public Response<Long> msetnx(byte[]... keysvalues) {
    String command = "msetnx";
    return instrumented(command, payloadSize(keysvalues), () -> delegated.msetnx(keysvalues));
  }

  @Override
  public Response<String> rename(String oldkey, String newkey) {
    String command = "rename";
    return instrumented(command, () -> delegated.rename(oldkey, newkey));
  }

  @Override
  public Response<String> rename(byte[] oldkey, byte[] newkey) {
    String command = "rename";
    return instrumented(command, () -> delegated.rename(oldkey, newkey));
  }

  @Override
  public Response<Long> renamenx(String oldkey, String newkey) {
    String command = "renamenx";
    return instrumented(command, () -> delegated.renamenx(oldkey, newkey));
  }

  @Override
  public Response<Long> renamenx(byte[] oldkey, byte[] newkey) {
    String command = "renamenx";
    return instrumented(command, () -> delegated.renamenx(oldkey, newkey));
  }

  @Override
  public Response<String> rpoplpush(String srckey, String dstkey) {
    String command = "rpoplpush";
    return instrumented(command, () -> delegated.rpoplpush(srckey, dstkey));
  }

  @Override
  public Response<byte[]> rpoplpush(byte[] srckey, byte[] dstkey) {
    String command = "rpoplpush";
    return instrumented(command, () -> delegated.rpoplpush(srckey, dstkey));
  }

  @Override
  public Response<Set<String>> sdiff(String... keys) {
    String command = "sdiff";
    return instrumented(command, () -> delegated.sdiff(keys));
  }

  @Override
  public Response<Set<byte[]>> sdiff(byte[]... keys) {
    String command = "sdiff";
    return instrumented(command, () -> delegated.sdiff(keys));
  }

  @Override
  public Response<Long> sdiffstore(String dstkey, String... keys) {
    String command = "sdiffstore";
    return instrumented(command, () -> delegated.sdiffstore(dstkey, keys));
  }

  @Override
  public Response<Long> sdiffstore(byte[] dstkey, byte[]... keys) {
    String command = "sdiffstore";
    return instrumented(command, () -> delegated.sdiffstore(dstkey, keys));
  }

  @Override
  public Response<Set<String>> sinter(String... keys) {
    String command = "sinter";
    return instrumented(command, () -> delegated.sinter(keys));
  }

  @Override
  public Response<Set<byte[]>> sinter(byte[]... keys) {
    String command = "sinter";
    return instrumented(command, () -> delegated.sinter(keys));
  }

  @Override
  public Response<Long> sinterstore(String dstkey, String... keys) {
    String command = "sinterstore";
    return instrumented(command, () -> delegated.sinterstore(dstkey, keys));
  }

  @Override
  public Response<Long> sinterstore(byte[] dstkey, byte[]... keys) {
    String command = "sinterstore";
    return instrumented(command, () -> delegated.sinterstore(dstkey, keys));
  }

  @Override
  public Response<Long> smove(String srckey, String dstkey, String member) {
    String command = "smove";
    return instrumented(command, () -> delegated.smove(srckey, dstkey, member));
  }

  @Override
  public Response<Long> smove(byte[] srckey, byte[] dstkey, byte[] member) {
    String command = "smove";
    return instrumented(command, () -> delegated.smove(srckey, dstkey, member));
  }

  @Override
  public Response<Long> sort(String key, SortingParams sortingParameters, String dstkey) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, sortingParameters, dstkey));
  }

  @Override
  public Response<Long> sort(byte[] key, SortingParams sortingParameters, byte[] dstkey) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, sortingParameters, dstkey));
  }

  @Override
  public Response<Long> sort(String key, String dstkey) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, dstkey));
  }

  @Override
  public Response<Long> sort(byte[] key, byte[] dstkey) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, dstkey));
  }

  @Override
  public Response<Set<String>> sunion(String... keys) {
    String command = "sunion";
    return instrumented(command, () -> delegated.sunion(keys));
  }

  @Override
  public Response<Set<byte[]>> sunion(byte[]... keys) {
    String command = "sunion";
    return instrumented(command, () -> delegated.sunion(keys));
  }

  @Override
  public Response<Long> sunionstore(String dstkey, String... keys) {
    String command = "sunionstore";
    return instrumented(command, () -> delegated.sunionstore(dstkey, keys));
  }

  @Override
  public Response<Long> sunionstore(byte[] dstkey, byte[]... keys) {
    String command = "sunionstore";
    return instrumented(command, () -> delegated.sunionstore(dstkey, keys));
  }

  @Override
  public Response<String> watch(String... keys) {
    String command = "watch";
    return instrumented(command, () -> delegated.watch(keys));
  }

  @Override
  public Response<String> watch(byte[]... keys) {
    String command = "watch";
    return instrumented(command, () -> delegated.watch(keys));
  }

  @Override
  public Response<Long> zinterstore(String dstkey, String... sets) {
    String command = "zinterstore";
    return instrumented(command, () -> delegated.zinterstore(dstkey, sets));
  }

  @Override
  public Response<Long> zinterstore(byte[] dstkey, byte[]... sets) {
    String command = "zinterstore";
    return instrumented(command, () -> delegated.zinterstore(dstkey, sets));
  }

  @Override
  public Response<Long> zinterstore(String dstkey, ZParams params, String... sets) {
    String command = "zinterstore";
    return instrumented(command, () -> delegated.zinterstore(dstkey, params, sets));
  }

  @Override
  public Response<Long> zinterstore(byte[] dstkey, ZParams params, byte[]... sets) {
    String command = "zinterstore";
    return instrumented(command, () -> delegated.zinterstore(dstkey, params, sets));
  }

  @Override
  public Response<Long> zunionstore(String dstkey, String... sets) {
    String command = "zunionstore";
    return instrumented(command, () -> delegated.zunionstore(dstkey, sets));
  }

  @Override
  public Response<Long> zunionstore(byte[] dstkey, byte[]... sets) {
    String command = "zunionstore";
    return instrumented(command, () -> delegated.zunionstore(dstkey, sets));
  }

  @Override
  public Response<Long> zunionstore(String dstkey, ZParams params, String... sets) {
    String command = "zunionstore";
    return instrumented(command, () -> delegated.zunionstore(dstkey, params, sets));
  }

  @Override
  public Response<Long> zunionstore(byte[] dstkey, ZParams params, byte[]... sets) {
    String command = "zunionstore";
    return instrumented(command, () -> delegated.zunionstore(dstkey, params, sets));
  }

  @Override
  public Response<String> bgrewriteaof() {
    String command = "bgrewriteaof";
    return instrumented(command, () -> delegated.bgrewriteaof());
  }

  @Override
  public Response<String> bgsave() {
    String command = "bgsave";
    return instrumented(command, () -> delegated.bgsave());
  }

  @Override
  public Response<List<String>> configGet(String pattern) {
    String command = "configGet";
    return instrumented(command, () -> delegated.configGet(pattern));
  }

  @Override
  public Response<String> configSet(String parameter, String value) {
    String command = "configSet";
    return instrumented(command, () -> delegated.configSet(parameter, value));
  }

  @Override
  public Response<String> brpoplpush(String source, String destination, int timeout) {
    String command = "brpoplpush";
    return instrumented(command, () -> delegated.brpoplpush(source, destination, timeout));
  }

  @Override
  public Response<byte[]> brpoplpush(byte[] source, byte[] destination, int timeout) {
    String command = "brpoplpush";
    return instrumented(command, () -> delegated.brpoplpush(source, destination, timeout));
  }

  @Override
  public Response<String> configResetStat() {
    String command = "configResetStat";
    return instrumented(command, () -> delegated.configResetStat());
  }

  @Override
  public Response<String> save() {
    String command = "save";
    return instrumented(command, () -> delegated.save());
  }

  @Override
  public Response<Long> lastsave() {
    String command = "lastsave";
    return instrumented(command, () -> delegated.lastsave());
  }

  @Override
  public Response<Long> publish(String channel, String message) {
    String command = "publish";
    return instrumented(command, payloadSize(message), () -> delegated.publish(channel, message));
  }

  @Override
  public Response<Long> publish(byte[] channel, byte[] message) {
    String command = "publish";
    return instrumented(command, payloadSize(message), () -> delegated.publish(channel, message));
  }

  @Override
  public Response<String> randomKey() {
    String command = "randomKey";
    return instrumented(command, () -> delegated.randomKey());
  }

  @Override
  public Response<byte[]> randomKeyBinary() {
    String command = "randomKeyBinary";
    return instrumented(command, () -> delegated.randomKeyBinary());
  }

  @Override
  public Response<String> flushDB() {
    String command = "flushDB";
    return instrumented(command, () -> delegated.flushDB());
  }

  @Override
  public Response<String> flushAll() {
    String command = "flushAll";
    return instrumented(command, () -> delegated.flushAll());
  }

  @Override
  public Response<String> info() {
    String command = "info";
    return instrumented(command, () -> delegated.info());
  }

  @Override
  public Response<String> info(String section) {
    String command = "info";
    return instrumented(command, () -> delegated.info(section));
  }

  @Override
  public Response<List<String>> time() {
    String command = "time";
    return instrumented(command, () -> delegated.time());
  }

  @Override
  public Response<Long> dbSize() {
    String command = "dbSize";
    return instrumented(command, () -> delegated.dbSize());
  }

  @Override
  public Response<String> shutdown() {
    String command = "shutdown";
    return instrumented(command, () -> delegated.shutdown());
  }

  @Override
  public Response<String> ping() {
    String command = "ping";
    return instrumented(command, () -> delegated.ping());
  }

  @Override
  public Response<String> select(int index) {
    String command = "select";
    return instrumented(command, () -> delegated.select(index));
  }

  @Override
  public Response<Long> bitop(BitOP op, byte[] destKey, byte[]... srcKeys) {
    String command = "bitop";
    return instrumented(command, () -> delegated.bitop(op, destKey, srcKeys));
  }

  @Override
  public Response<Long> bitop(BitOP op, String destKey, String... srcKeys) {
    String command = "bitop";
    return instrumented(command, () -> delegated.bitop(op, destKey, srcKeys));
  }

  @Override
  public Response<String> clusterNodes() {
    String command = "clusterNodes";
    return instrumented(command, () -> delegated.clusterNodes());
  }

  @Override
  public Response<String> clusterMeet(String ip, int port) {
    String command = "clusterMeet";
    return instrumented(command, () -> delegated.clusterMeet(ip, port));
  }

  @Override
  public Response<String> clusterAddSlots(int... slots) {
    String command = "clusterAddSlots";
    return instrumented(command, () -> delegated.clusterAddSlots(slots));
  }

  @Override
  public Response<String> clusterDelSlots(int... slots) {
    String command = "clusterDelSlots";
    return instrumented(command, () -> delegated.clusterDelSlots(slots));
  }

  @Override
  public Response<String> clusterInfo() {
    String command = "clusterInfo";
    return instrumented(command, () -> delegated.clusterInfo());
  }

  @Override
  public Response<List<String>> clusterGetKeysInSlot(int slot, int count) {
    String command = "clusterGetKeysInSlot";
    return instrumented(command, () -> delegated.clusterGetKeysInSlot(slot, count));
  }

  @Override
  public Response<String> clusterSetSlotNode(int slot, String nodeId) {
    String command = "clusterSetSlotNode";
    return instrumented(command, () -> delegated.clusterSetSlotNode(slot, nodeId));
  }

  @Override
  public Response<String> clusterSetSlotMigrating(int slot, String nodeId) {
    String command = "clusterSetSlotMigrating";
    return instrumented(command, () -> delegated.clusterSetSlotMigrating(slot, nodeId));
  }

  @Override
  public Response<String> clusterSetSlotImporting(int slot, String nodeId) {
    String command = "clusterSetSlotImporting";
    return instrumented(command, () -> delegated.clusterSetSlotImporting(slot, nodeId));
  }

  @Override
  public Response<Object> eval(byte[] script) {
    String command = "eval";
    return instrumented(command, payloadSize(script), () -> delegated.eval(script));
  }

  @Override
  public Response<Object> eval(byte[] script, byte[] keyCount, byte[]... params) {
    String command = "eval";
    return instrumented(
        command,
        payloadSize(script) + payloadSize(params),
        () -> delegated.eval(script, keyCount, params));
  }

  @Override
  public Response<Object> eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
    String command = "eval";
    return instrumented(
        command, payloadSize(script) + payloadSize(args), () -> delegated.eval(script, keys, args));
  }

  @Override
  public Response<Object> eval(byte[] script, int keyCount, byte[]... params) {
    String command = "eval";
    return instrumented(
        command,
        payloadSize(script) + payloadSize(params),
        () -> delegated.eval(script, keyCount, params));
  }

  @Override
  public Response<Object> evalsha(byte[] sha1) {
    String command = "evalsha";
    return instrumented(command, () -> delegated.evalsha(sha1));
  }

  @Override
  public Response<Object> evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
    String command = "evalsha";
    return instrumented(command, payloadSize(args), () -> delegated.evalsha(sha1, keys, args));
  }

  @Override
  public Response<Object> evalsha(byte[] sha1, int keyCount, byte[]... params) {
    String command = "evalsha";
    return instrumented(
        command, payloadSize(params), () -> delegated.evalsha(sha1, keyCount, params));
  }

  @Override
  public Response<String> pfmerge(byte[] destkey, byte[]... sourcekeys) {
    String command = "pfmerge";
    return instrumented(command, () -> delegated.pfmerge(destkey, sourcekeys));
  }

  @Override
  public Response<String> pfmerge(String destkey, String... sourcekeys) {
    String command = "pfmerge";
    return instrumented(command, () -> delegated.pfmerge(destkey, sourcekeys));
  }

  @Override
  public Response<Long> pfcount(String... keys) {
    String command = "pfcount";
    return instrumented(command, () -> delegated.pfcount(keys));
  }

  @Override
  public Response<Long> pfcount(byte[]... keys) {
    String command = "pfcount";
    return instrumented(command, () -> delegated.pfcount(keys));
  }

  @Override
  public Response<Long> append(String key, String value) {
    String command = "append";
    return instrumented(command, payloadSize(value), () -> delegated.append(key, value));
  }

  @Override
  public Response<Long> append(byte[] key, byte[] value) {
    String command = "append";
    return instrumented(command, payloadSize(value), () -> delegated.append(key, value));
  }

  @Override
  public Response<List<String>> blpop(String key) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(key));
  }

  @Override
  public Response<List<String>> brpop(String key) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(key));
  }

  @Override
  public Response<List<byte[]>> blpop(byte[] key) {
    String command = "blpop";
    return instrumented(command, () -> delegated.blpop(key));
  }

  @Override
  public Response<List<byte[]>> brpop(byte[] key) {
    String command = "brpop";
    return instrumented(command, () -> delegated.brpop(key));
  }

  @Override
  public Response<Long> decr(String key) {
    String command = "decr";
    return instrumented(command, () -> delegated.decr(key));
  }

  @Override
  public Response<Long> decr(byte[] key) {
    String command = "decr";
    return instrumented(command, () -> delegated.decr(key));
  }

  @Override
  public Response<Long> decrBy(String key, long integer) {
    String command = "decrBy";
    return instrumented(command, () -> delegated.decrBy(key, integer));
  }

  @Override
  public Response<Long> decrBy(byte[] key, long integer) {
    String command = "decrBy";
    return instrumented(command, () -> delegated.decrBy(key, integer));
  }

  @Override
  public Response<Long> del(String key) {
    String command = "del";
    return instrumented(command, () -> delegated.del(key));
  }

  @Override
  public Response<Long> del(byte[] key) {
    String command = "del";
    return instrumented(command, () -> delegated.del(key));
  }

  @Override
  public Response<String> echo(String string) {
    String command = "echo";
    return instrumented(command, () -> delegated.echo(string));
  }

  @Override
  public Response<byte[]> echo(byte[] string) {
    String command = "echo";
    return instrumented(command, () -> delegated.echo(string));
  }

  @Override
  public Response<Boolean> exists(String key) {
    String command = "exists";
    return instrumented(command, () -> delegated.exists(key));
  }

  @Override
  public Response<Boolean> exists(byte[] key) {
    String command = "exists";
    return instrumented(command, () -> delegated.exists(key));
  }

  @Override
  public Response<Long> expire(String key, int seconds) {
    String command = "expire";
    return instrumented(command, () -> delegated.expire(key, seconds));
  }

  @Override
  public Response<Long> expire(byte[] key, int seconds) {
    String command = "expire";
    return instrumented(command, () -> delegated.expire(key, seconds));
  }

  @Override
  public Response<Long> expireAt(String key, long unixTime) {
    String command = "expireAt";
    return instrumented(command, () -> delegated.expireAt(key, unixTime));
  }

  @Override
  public Response<Long> expireAt(byte[] key, long unixTime) {
    String command = "expireAt";
    return instrumented(command, () -> delegated.expireAt(key, unixTime));
  }

  @Override
  public Response<String> get(String key) {
    String command = "get";
    return instrumented(command, () -> delegated.get(key));
  }

  @Override
  public Response<byte[]> get(byte[] key) {
    String command = "get";
    return instrumented(command, () -> delegated.get(key));
  }

  @Override
  public Response<Boolean> getbit(String key, long offset) {
    String command = "getbit";
    return instrumented(command, () -> delegated.getbit(key, offset));
  }

  @Override
  public Response<Boolean> getbit(byte[] key, long offset) {
    String command = "getbit";
    return instrumented(command, () -> delegated.getbit(key, offset));
  }

  @Override
  public Response<Long> bitpos(String key, boolean value) {
    String command = "bitpos";
    return instrumented(command, () -> delegated.bitpos(key, value));
  }

  @Override
  public Response<Long> bitpos(String key, boolean value, BitPosParams params) {
    String command = "bitpos";
    return instrumented(command, () -> delegated.bitpos(key, value, params));
  }

  @Override
  public Response<Long> bitpos(byte[] key, boolean value) {
    String command = "bitpos";
    return instrumented(command, () -> delegated.bitpos(key, value));
  }

  @Override
  public Response<Long> bitpos(byte[] key, boolean value, BitPosParams params) {
    String command = "bitpos";
    return instrumented(command, () -> delegated.bitpos(key, value, params));
  }

  @Override
  public Response<String> getrange(String key, long startOffset, long endOffset) {
    String command = "getrange";
    return instrumented(command, () -> delegated.getrange(key, startOffset, endOffset));
  }

  @Override
  public Response<String> getSet(String key, String value) {
    String command = "getSet";
    return instrumented(command, payloadSize(value), () -> delegated.getSet(key, value));
  }

  @Override
  public Response<byte[]> getSet(byte[] key, byte[] value) {
    String command = "getSet";
    return instrumented(command, payloadSize(value), () -> delegated.getSet(key, value));
  }

  @Override
  public Response<byte[]> getrange(byte[] key, long startOffset, long endOffset) {
    String command = "getrange";
    return instrumented(command, () -> delegated.getrange(key, startOffset, endOffset));
  }

  @Override
  public Response<Long> hdel(String key, String... field) {
    String command = "hdel";
    return instrumented(command, () -> delegated.hdel(key, field));
  }

  @Override
  public Response<Long> hdel(byte[] key, byte[]... field) {
    String command = "hdel";
    return instrumented(command, () -> delegated.hdel(key, field));
  }

  @Override
  public Response<Boolean> hexists(String key, String field) {
    String command = "hexists";
    return instrumented(command, () -> delegated.hexists(key, field));
  }

  @Override
  public Response<Boolean> hexists(byte[] key, byte[] field) {
    String command = "hexists";
    return instrumented(command, () -> delegated.hexists(key, field));
  }

  @Override
  public Response<String> hget(String key, String field) {
    String command = "hget";
    return instrumented(command, () -> delegated.hget(key, field));
  }

  @Override
  public Response<byte[]> hget(byte[] key, byte[] field) {
    String command = "hget";
    return instrumented(command, () -> delegated.hget(key, field));
  }

  @Override
  public Response<Map<String, String>> hgetAll(String key) {
    String command = "hgetAll";
    return instrumented(command, () -> delegated.hgetAll(key));
  }

  @Override
  public Response<Map<byte[], byte[]>> hgetAll(byte[] key) {
    String command = "hgetAll";
    return instrumented(command, () -> delegated.hgetAll(key));
  }

  @Override
  public Response<Long> hincrBy(String key, String field, long value) {
    String command = "hincrBy";
    return instrumented(command, () -> delegated.hincrBy(key, field, value));
  }

  @Override
  public Response<Long> hincrBy(byte[] key, byte[] field, long value) {
    String command = "hincrBy";
    return instrumented(command, () -> delegated.hincrBy(key, field, value));
  }

  @Override
  public Response<Set<String>> hkeys(String key) {
    String command = "hkeys";
    return instrumented(command, () -> delegated.hkeys(key));
  }

  @Override
  public Response<Set<byte[]>> hkeys(byte[] key) {
    String command = "hkeys";
    return instrumented(command, () -> delegated.hkeys(key));
  }

  @Override
  public Response<Long> hlen(String key) {
    String command = "hlen";
    return instrumented(command, () -> delegated.hlen(key));
  }

  @Override
  public Response<Long> hlen(byte[] key) {
    String command = "hlen";
    return instrumented(command, () -> delegated.hlen(key));
  }

  @Override
  public Response<List<String>> hmget(String key, String... fields) {
    String command = "hmget";
    return instrumented(command, () -> delegated.hmget(key, fields));
  }

  @Override
  public Response<List<byte[]>> hmget(byte[] key, byte[]... fields) {
    String command = "hmget";
    return instrumented(command, () -> delegated.hmget(key, fields));
  }

  @Override
  public Response<String> hmset(String key, Map<String, String> hash) {
    String command = "hmset";
    return instrumented(command, () -> delegated.hmset(key, hash));
  }

  @Override
  public Response<String> hmset(byte[] key, Map<byte[], byte[]> hash) {
    String command = "hmset";
    return instrumented(command, () -> delegated.hmset(key, hash));
  }

  @Override
  public Response<Long> hset(String key, String field, String value) {
    String command = "hset";
    return instrumented(command, payloadSize(value), () -> delegated.hset(key, field, value));
  }

  @Override
  public Response<Long> hset(byte[] key, byte[] field, byte[] value) {
    String command = "hset";
    return instrumented(command, payloadSize(value), () -> delegated.hset(key, field, value));
  }

  @Override
  public Response<Long> hsetnx(String key, String field, String value) {
    String command = "hsetnx";
    return instrumented(command, payloadSize(value), () -> delegated.hsetnx(key, field, value));
  }

  @Override
  public Response<Long> hsetnx(byte[] key, byte[] field, byte[] value) {
    String command = "hsetnx";
    return instrumented(command, payloadSize(value), () -> delegated.hsetnx(key, field, value));
  }

  @Override
  public Response<List<String>> hvals(String key) {
    String command = "hvals";
    return instrumented(command, () -> delegated.hvals(key));
  }

  @Override
  public Response<List<byte[]>> hvals(byte[] key) {
    String command = "hvals";
    return instrumented(command, () -> delegated.hvals(key));
  }

  @Override
  public Response<Long> incr(String key) {
    String command = "incr";
    return instrumented(command, () -> delegated.incr(key));
  }

  @Override
  public Response<Long> incr(byte[] key) {
    String command = "incr";
    return instrumented(command, () -> delegated.incr(key));
  }

  @Override
  public Response<Long> incrBy(String key, long integer) {
    String command = "incrBy";
    return instrumented(command, () -> delegated.incrBy(key, integer));
  }

  @Override
  public Response<Long> incrBy(byte[] key, long integer) {
    String command = "incrBy";
    return instrumented(command, () -> delegated.incrBy(key, integer));
  }

  @Override
  public Response<String> lindex(String key, long index) {
    String command = "lindex";
    return instrumented(command, () -> delegated.lindex(key, index));
  }

  @Override
  public Response<byte[]> lindex(byte[] key, long index) {
    String command = "lindex";
    return instrumented(command, () -> delegated.lindex(key, index));
  }

  @Override
  public Response<Long> llen(String key) {
    String command = "llen";
    return instrumented(command, () -> delegated.llen(key));
  }

  @Override
  public Response<Long> llen(byte[] key) {
    String command = "llen";
    return instrumented(command, () -> delegated.llen(key));
  }

  @Override
  public Response<String> lpop(String key) {
    String command = "lpop";
    return instrumented(command, () -> delegated.lpop(key));
  }

  @Override
  public Response<byte[]> lpop(byte[] key) {
    String command = "lpop";
    return instrumented(command, () -> delegated.lpop(key));
  }

  @Override
  public Response<Long> lpush(String key, String... string) {
    String command = "lpush";
    return instrumented(command, payloadSize(string), () -> delegated.lpush(key, string));
  }

  @Override
  public Response<Long> lpush(byte[] key, byte[]... string) {
    String command = "lpush";
    return instrumented(command, payloadSize(string), () -> delegated.lpush(key, string));
  }

  @Override
  public Response<Long> lpushx(String key, String... string) {
    String command = "lpushx";
    return instrumented(command, payloadSize(string), () -> delegated.lpushx(key, string));
  }

  @Override
  public Response<Long> lpushx(byte[] key, byte[]... bytes) {
    String command = "lpushx";
    return instrumented(command, payloadSize(bytes), () -> delegated.lpushx(key, bytes));
  }

  @Override
  public Response<List<String>> lrange(String key, long start, long end) {
    String command = "lrange";
    return instrumented(command, () -> delegated.lrange(key, start, end));
  }

  @Override
  public Response<List<byte[]>> lrange(byte[] key, long start, long end) {
    String command = "lrange";
    return instrumented(command, () -> delegated.lrange(key, start, end));
  }

  @Override
  public Response<Long> lrem(String key, long count, String value) {
    String command = "lrem";
    return instrumented(command, payloadSize(value), () -> delegated.lrem(key, count, value));
  }

  @Override
  public Response<Long> lrem(byte[] key, long count, byte[] value) {
    String command = "lrem";
    return instrumented(command, payloadSize(value), () -> delegated.lrem(key, count, value));
  }

  @Override
  public Response<String> lset(String key, long index, String value) {
    String command = "lset";
    return instrumented(command, payloadSize(value), () -> delegated.lset(key, index, value));
  }

  @Override
  public Response<String> lset(byte[] key, long index, byte[] value) {
    String command = "lset";
    return instrumented(command, payloadSize(value), () -> delegated.lset(key, index, value));
  }

  @Override
  public Response<String> ltrim(String key, long start, long end) {
    String command = "ltrim";
    return instrumented(command, () -> delegated.ltrim(key, start, end));
  }

  @Override
  public Response<String> ltrim(byte[] key, long start, long end) {
    String command = "ltrim";
    return instrumented(command, () -> delegated.ltrim(key, start, end));
  }

  @Override
  public Response<Long> move(String key, int dbIndex) {
    String command = "move";
    return instrumented(command, () -> delegated.move(key, dbIndex));
  }

  @Override
  public Response<Long> move(byte[] key, int dbIndex) {
    String command = "move";
    return instrumented(command, () -> delegated.move(key, dbIndex));
  }

  @Override
  public Response<Long> persist(String key) {
    String command = "persist";
    return instrumented(command, () -> delegated.persist(key));
  }

  @Override
  public Response<Long> persist(byte[] key) {
    String command = "persist";
    return instrumented(command, () -> delegated.persist(key));
  }

  @Override
  public Response<String> rpop(String key) {
    String command = "rpop";
    return instrumented(command, () -> delegated.rpop(key));
  }

  @Override
  public Response<byte[]> rpop(byte[] key) {
    String command = "rpop";
    return instrumented(command, () -> delegated.rpop(key));
  }

  @Override
  public Response<Long> rpush(String key, String... string) {
    String command = "rpush";
    return instrumented(command, payloadSize(string), () -> delegated.rpush(key, string));
  }

  @Override
  public Response<Long> rpush(byte[] key, byte[]... string) {
    String command = "rpush";
    return instrumented(command, payloadSize(string), () -> delegated.rpush(key, string));
  }

  @Override
  public Response<Long> rpushx(String key, String... string) {
    String command = "rpushx";
    return instrumented(command, payloadSize(string), () -> delegated.rpushx(key, string));
  }

  @Override
  public Response<Long> rpushx(byte[] key, byte[]... string) {
    String command = "rpushx";
    return instrumented(command, payloadSize(string), () -> delegated.rpushx(key, string));
  }

  @Override
  public Response<Long> sadd(String key, String... member) {
    String command = "sadd";
    return instrumented(command, payloadSize(member), () -> delegated.sadd(key, member));
  }

  @Override
  public Response<Long> sadd(byte[] key, byte[]... member) {
    String command = "sadd";
    return instrumented(command, payloadSize(member), () -> delegated.sadd(key, member));
  }

  @Override
  public Response<Long> scard(String key) {
    String command = "scard";
    return instrumented(command, () -> delegated.scard(key));
  }

  @Override
  public Response<Long> scard(byte[] key) {
    String command = "scard";
    return instrumented(command, () -> delegated.scard(key));
  }

  @Override
  public Response<String> set(String key, String value) {
    String command = "set";
    return instrumented(command, payloadSize(value), () -> delegated.set(key, value));
  }

  @Override
  public Response<String> set(byte[] key, byte[] value) {
    String command = "set";
    return instrumented(command, payloadSize(value), () -> delegated.set(key, value));
  }

  @Override
  public Response<Boolean> setbit(String key, long offset, boolean value) {
    String command = "setbit";
    return instrumented(command, () -> delegated.setbit(key, offset, value));
  }

  @Override
  public Response<Boolean> setbit(byte[] key, long offset, byte[] value) {
    String command = "setbit";
    return instrumented(command, () -> delegated.setbit(key, offset, value));
  }

  @Override
  public Response<String> setex(String key, int seconds, String value) {
    String command = "setex";
    return instrumented(command, payloadSize(value), () -> delegated.setex(key, seconds, value));
  }

  @Override
  public Response<String> setex(byte[] key, int seconds, byte[] value) {
    String command = "setex";
    return instrumented(command, payloadSize(value), () -> delegated.setex(key, seconds, value));
  }

  @Override
  public Response<Long> setnx(String key, String value) {
    String command = "setnx";
    return instrumented(command, payloadSize(value), () -> delegated.setnx(key, value));
  }

  @Override
  public Response<Long> setnx(byte[] key, byte[] value) {
    String command = "setnx";
    return instrumented(command, payloadSize(value), () -> delegated.setnx(key, value));
  }

  @Override
  public Response<Long> setrange(String key, long offset, String value) {
    String command = "setrange";
    return instrumented(command, payloadSize(value), () -> delegated.setrange(key, offset, value));
  }

  @Override
  public Response<Long> setrange(byte[] key, long offset, byte[] value) {
    String command = "setrange";
    return instrumented(command, payloadSize(value), () -> delegated.setrange(key, offset, value));
  }

  @Override
  public Response<Boolean> sismember(String key, String member) {
    String command = "sismember";
    return instrumented(command, () -> delegated.sismember(key, member));
  }

  @Override
  public Response<Boolean> sismember(byte[] key, byte[] member) {
    String command = "sismember";
    return instrumented(command, () -> delegated.sismember(key, member));
  }

  @Override
  public Response<Set<String>> smembers(String key) {
    String command = "smembers";
    return instrumented(command, () -> delegated.smembers(key));
  }

  @Override
  public Response<Set<byte[]>> smembers(byte[] key) {
    String command = "smembers";
    return instrumented(command, () -> delegated.smembers(key));
  }

  @Override
  public Response<List<String>> sort(String key) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key));
  }

  @Override
  public Response<List<byte[]>> sort(byte[] key) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key));
  }

  @Override
  public Response<List<String>> sort(String key, SortingParams sortingParameters) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, sortingParameters));
  }

  @Override
  public Response<List<byte[]>> sort(byte[] key, SortingParams sortingParameters) {
    String command = "sort";
    return instrumented(command, () -> delegated.sort(key, sortingParameters));
  }

  @Override
  public Response<String> spop(String key) {
    String command = "spop";
    return instrumented(command, () -> delegated.spop(key));
  }

  @Override
  public Response<Set<String>> spop(String key, long count) {
    String command = "spop";
    return instrumented(command, () -> delegated.spop(key, count));
  }

  @Override
  public Response<byte[]> spop(byte[] key) {
    String command = "spop";
    return instrumented(command, () -> delegated.spop(key));
  }

  @Override
  public Response<Set<byte[]>> spop(byte[] key, long count) {
    String command = "spop";
    return instrumented(command, () -> delegated.spop(key, count));
  }

  @Override
  public Response<String> srandmember(String key) {
    String command = "srandmember";
    return instrumented(command, () -> delegated.srandmember(key));
  }

  @Override
  public Response<List<String>> srandmember(String key, int count) {
    String command = "srandmember";
    return instrumented(command, () -> delegated.srandmember(key, count));
  }

  @Override
  public Response<byte[]> srandmember(byte[] key) {
    String command = "srandmember";
    return instrumented(command, () -> delegated.srandmember(key));
  }

  @Override
  public Response<List<byte[]>> srandmember(byte[] key, int count) {
    String command = "srandmember";
    return instrumented(command, () -> delegated.srandmember(key, count));
  }

  @Override
  public Response<Long> srem(String key, String... member) {
    String command = "srem";
    return instrumented(command, () -> delegated.srem(key, member));
  }

  @Override
  public Response<Long> srem(byte[] key, byte[]... member) {
    String command = "srem";
    return instrumented(command, () -> delegated.srem(key, member));
  }

  @Override
  public Response<Long> strlen(String key) {
    String command = "strlen";
    return instrumented(command, () -> delegated.strlen(key));
  }

  @Override
  public Response<Long> strlen(byte[] key) {
    String command = "strlen";
    return instrumented(command, () -> delegated.strlen(key));
  }

  @Override
  public Response<String> substr(String key, int start, int end) {
    String command = "substr";
    return instrumented(command, () -> delegated.substr(key, start, end));
  }

  @Override
  public Response<String> substr(byte[] key, int start, int end) {
    String command = "substr";
    return instrumented(command, () -> delegated.substr(key, start, end));
  }

  @Override
  public Response<Long> ttl(String key) {
    String command = "ttl";
    return instrumented(command, () -> delegated.ttl(key));
  }

  @Override
  public Response<Long> ttl(byte[] key) {
    String command = "ttl";
    return instrumented(command, () -> delegated.ttl(key));
  }

  @Override
  public Response<String> type(String key) {
    String command = "type";
    return instrumented(command, () -> delegated.type(key));
  }

  @Override
  public Response<String> type(byte[] key) {
    String command = "type";
    return instrumented(command, () -> delegated.type(key));
  }

  @Override
  public Response<Long> zadd(String key, double score, String member) {
    String command = "zadd";
    return instrumented(command, payloadSize(member), () -> delegated.zadd(key, score, member));
  }

  @Override
  public Response<Long> zadd(String key, double score, String member, ZAddParams params) {
    String command = "zadd";
    return instrumented(
        command, payloadSize(member), () -> delegated.zadd(key, score, member, params));
  }

  @Override
  public Response<Long> zadd(String key, Map<String, Double> scoreMembers) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, scoreMembers));
  }

  @Override
  public Response<Long> zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, scoreMembers, params));
  }

  @Override
  public Response<Long> zadd(byte[] key, double score, byte[] member) {
    String command = "zadd";
    return instrumented(command, payloadSize(member), () -> delegated.zadd(key, score, member));
  }

  @Override
  public Response<Long> zadd(byte[] key, double score, byte[] member, ZAddParams params) {
    String command = "zadd";
    return instrumented(
        command, payloadSize(member), () -> delegated.zadd(key, score, member, params));
  }

  @Override
  public Response<Long> zadd(byte[] key, Map<byte[], Double> scoreMembers) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, scoreMembers));
  }

  @Override
  public Response<Long> zadd(byte[] key, Map<byte[], Double> scoreMembers, ZAddParams params) {
    String command = "zadd";
    return instrumented(command, () -> delegated.zadd(key, scoreMembers, params));
  }

  @Override
  public Response<Long> zcard(String key) {
    String command = "zcard";
    return instrumented(command, () -> delegated.zcard(key));
  }

  @Override
  public Response<Long> zcard(byte[] key) {
    String command = "zcard";
    return instrumented(command, () -> delegated.zcard(key));
  }

  @Override
  public Response<Long> zcount(String key, double min, double max) {
    String command = "zcount";
    return instrumented(command, () -> delegated.zcount(key, min, max));
  }

  @Override
  public Response<Long> zcount(String key, String min, String max) {
    String command = "zcount";
    return instrumented(command, () -> delegated.zcount(key, min, max));
  }

  @Override
  public Response<Long> zcount(byte[] key, double min, double max) {
    String command = "zcount";
    return instrumented(command, () -> delegated.zcount(key, min, max));
  }

  @Override
  public Response<Long> zcount(byte[] key, byte[] min, byte[] max) {
    String command = "zcount";
    return instrumented(command, () -> delegated.zcount(key, min, max));
  }

  @Override
  public Response<Double> zincrby(String key, double score, String member) {
    String command = "zincrby";
    return instrumented(command, () -> delegated.zincrby(key, score, member));
  }

  @Override
  public Response<Double> zincrby(String key, double score, String member, ZIncrByParams params) {
    String command = "zincrby";
    return instrumented(command, () -> delegated.zincrby(key, score, member, params));
  }

  @Override
  public Response<Double> zincrby(byte[] key, double score, byte[] member) {
    String command = "zincrby";
    return instrumented(command, () -> delegated.zincrby(key, score, member));
  }

  @Override
  public Response<Double> zincrby(byte[] key, double score, byte[] member, ZIncrByParams params) {
    String command = "zincrby";
    return instrumented(command, () -> delegated.zincrby(key, score, member, params));
  }

  @Override
  public Response<Set<String>> zrange(String key, long start, long end) {
    String command = "zrange";
    return instrumented(command, () -> delegated.zrange(key, start, end));
  }

  @Override
  public Response<Set<byte[]>> zrange(byte[] key, long start, long end) {
    String command = "zrange";
    return instrumented(command, () -> delegated.zrange(key, start, end));
  }

  @Override
  public Response<Set<String>> zrangeByScore(String key, double min, double max) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max));
  }

  @Override
  public Response<Set<byte[]>> zrangeByScore(byte[] key, double min, double max) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max));
  }

  @Override
  public Response<Set<String>> zrangeByScore(String key, String min, String max) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max));
  }

  @Override
  public Response<Set<byte[]>> zrangeByScore(byte[] key, byte[] min, byte[] max) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max));
  }

  @Override
  public Response<Set<String>> zrangeByScore(
      String key, double min, double max, int offset, int count) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<Set<String>> zrangeByScore(
      String key, String min, String max, int offset, int count) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<Set<byte[]>> zrangeByScore(
      byte[] key, double min, double max, int offset, int count) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<Set<byte[]>> zrangeByScore(
      byte[] key, byte[] min, byte[] max, int offset, int count) {
    String command = "zrangeByScore";
    return instrumented(command, () -> delegated.zrangeByScore(key, min, max, offset, count));
  }

  @Override
  public Response<Set<Tuple>> zrangeByScoreWithScores(String key, double min, double max) {
    String command = "zrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Response<Set<Tuple>> zrangeByScoreWithScores(String key, String min, String max) {
    String command = "zrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Response<Set<Tuple>> zrangeByScoreWithScores(byte[] key, double min, double max) {
    String command = "zrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Response<Set<Tuple>> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max) {
    String command = "zrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrangeByScoreWithScores(key, min, max));
  }

  @Override
  public Response<Set<Tuple>> zrangeByScoreWithScores(
      String key, double min, double max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Response<Set<Tuple>> zrangeByScoreWithScores(
      String key, String min, String max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Response<Set<Tuple>> zrangeByScoreWithScores(
      byte[] key, double min, double max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Response<Set<Tuple>> zrangeByScoreWithScores(
      byte[] key, byte[] min, byte[] max, int offset, int count) {
    String command = "zrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrangeByScoreWithScores(key, min, max, offset, count));
  }

  @Override
  public Response<Set<String>> zrevrangeByScore(String key, double max, double min) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min));
  }

  @Override
  public Response<Set<byte[]>> zrevrangeByScore(byte[] key, double max, double min) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min));
  }

  @Override
  public Response<Set<String>> zrevrangeByScore(String key, String max, String min) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min));
  }

  @Override
  public Response<Set<byte[]>> zrevrangeByScore(byte[] key, byte[] max, byte[] min) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min));
  }

  @Override
  public Response<Set<String>> zrevrangeByScore(
      String key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Response<Set<String>> zrevrangeByScore(
      String key, String max, String min, int offset, int count) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Response<Set<byte[]>> zrevrangeByScore(
      byte[] key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Response<Set<byte[]>> zrevrangeByScore(
      byte[] key, byte[] max, byte[] min, int offset, int count) {
    String command = "zrevrangeByScore";
    return instrumented(command, () -> delegated.zrevrangeByScore(key, max, min, offset, count));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeByScoreWithScores(String key, double max, double min) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeByScoreWithScores(String key, String max, String min) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeByScoreWithScores(byte[] key, double max, double min) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(command, () -> delegated.zrevrangeByScoreWithScores(key, max, min));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeByScoreWithScores(
      String key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeByScoreWithScores(
      String key, String max, String min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeByScoreWithScores(
      byte[] key, double max, double min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeByScoreWithScores(
      byte[] key, byte[] max, byte[] min, int offset, int count) {
    String command = "zrevrangeByScoreWithScores";
    return instrumented(
        command, () -> delegated.zrevrangeByScoreWithScores(key, max, min, offset, count));
  }

  @Override
  public Response<Set<Tuple>> zrangeWithScores(String key, long start, long end) {
    String command = "zrangeWithScores";
    return instrumented(command, () -> delegated.zrangeWithScores(key, start, end));
  }

  @Override
  public Response<Set<Tuple>> zrangeWithScores(byte[] key, long start, long end) {
    String command = "zrangeWithScores";
    return instrumented(command, () -> delegated.zrangeWithScores(key, start, end));
  }

  @Override
  public Response<Long> zrank(String key, String member) {
    String command = "zrank";
    return instrumented(command, () -> delegated.zrank(key, member));
  }

  @Override
  public Response<Long> zrank(byte[] key, byte[] member) {
    String command = "zrank";
    return instrumented(command, () -> delegated.zrank(key, member));
  }

  @Override
  public Response<Long> zrem(String key, String... member) {
    String command = "zrem";
    return instrumented(command, () -> delegated.zrem(key, member));
  }

  @Override
  public Response<Long> zrem(byte[] key, byte[]... member) {
    String command = "zrem";
    return instrumented(command, () -> delegated.zrem(key, member));
  }

  @Override
  public Response<Long> zremrangeByRank(String key, long start, long end) {
    String command = "zremrangeByRank";
    return instrumented(command, () -> delegated.zremrangeByRank(key, start, end));
  }

  @Override
  public Response<Long> zremrangeByRank(byte[] key, long start, long end) {
    String command = "zremrangeByRank";
    return instrumented(command, () -> delegated.zremrangeByRank(key, start, end));
  }

  @Override
  public Response<Long> zremrangeByScore(String key, double start, double end) {
    String command = "zremrangeByScore";
    return instrumented(command, () -> delegated.zremrangeByScore(key, start, end));
  }

  @Override
  public Response<Long> zremrangeByScore(String key, String start, String end) {
    String command = "zremrangeByScore";
    return instrumented(command, () -> delegated.zremrangeByScore(key, start, end));
  }

  @Override
  public Response<Long> zremrangeByScore(byte[] key, double start, double end) {
    String command = "zremrangeByScore";
    return instrumented(command, () -> delegated.zremrangeByScore(key, start, end));
  }

  @Override
  public Response<Long> zremrangeByScore(byte[] key, byte[] start, byte[] end) {
    String command = "zremrangeByScore";
    return instrumented(command, () -> delegated.zremrangeByScore(key, start, end));
  }

  @Override
  public Response<Set<String>> zrevrange(String key, long start, long end) {
    String command = "zrevrange";
    return instrumented(command, () -> delegated.zrevrange(key, start, end));
  }

  @Override
  public Response<Set<byte[]>> zrevrange(byte[] key, long start, long end) {
    String command = "zrevrange";
    return instrumented(command, () -> delegated.zrevrange(key, start, end));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeWithScores(String key, long start, long end) {
    String command = "zrevrangeWithScores";
    return instrumented(command, () -> delegated.zrevrangeWithScores(key, start, end));
  }

  @Override
  public Response<Set<Tuple>> zrevrangeWithScores(byte[] key, long start, long end) {
    String command = "zrevrangeWithScores";
    return instrumented(command, () -> delegated.zrevrangeWithScores(key, start, end));
  }

  @Override
  public Response<Long> zrevrank(String key, String member) {
    String command = "zrevrank";
    return instrumented(command, () -> delegated.zrevrank(key, member));
  }

  @Override
  public Response<Long> zrevrank(byte[] key, byte[] member) {
    String command = "zrevrank";
    return instrumented(command, () -> delegated.zrevrank(key, member));
  }

  @Override
  public Response<Double> zscore(String key, String member) {
    String command = "zscore";
    return instrumented(command, () -> delegated.zscore(key, member));
  }

  @Override
  public Response<Double> zscore(byte[] key, byte[] member) {
    String command = "zscore";
    return instrumented(command, () -> delegated.zscore(key, member));
  }

  @Override
  public Response<Long> zlexcount(byte[] key, byte[] min, byte[] max) {
    String command = "zlexcount";
    return instrumented(command, () -> delegated.zlexcount(key, min, max));
  }

  @Override
  public Response<Long> zlexcount(String key, String min, String max) {
    String command = "zlexcount";
    return instrumented(command, () -> delegated.zlexcount(key, min, max));
  }

  @Override
  public Response<Set<byte[]>> zrangeByLex(byte[] key, byte[] min, byte[] max) {
    String command = "zrangeByLex";
    return instrumented(command, () -> delegated.zrangeByLex(key, min, max));
  }

  @Override
  public Response<Set<String>> zrangeByLex(String key, String min, String max) {
    String command = "zrangeByLex";
    return instrumented(command, () -> delegated.zrangeByLex(key, min, max));
  }

  @Override
  public Response<Set<byte[]>> zrangeByLex(
      byte[] key, byte[] min, byte[] max, int offset, int count) {
    String command = "zrangeByLex";
    return instrumented(command, () -> delegated.zrangeByLex(key, min, max, offset, count));
  }

  @Override
  public Response<Set<String>> zrangeByLex(
      String key, String min, String max, int offset, int count) {
    String command = "zrangeByLex";
    return instrumented(command, () -> delegated.zrangeByLex(key, min, max, offset, count));
  }

  @Override
  public Response<Set<byte[]>> zrevrangeByLex(byte[] key, byte[] max, byte[] min) {
    String command = "zrevrangeByLex";
    return instrumented(command, () -> delegated.zrevrangeByLex(key, max, min));
  }

  @Override
  public Response<Set<String>> zrevrangeByLex(String key, String max, String min) {
    String command = "zrevrangeByLex";
    return instrumented(command, () -> delegated.zrevrangeByLex(key, max, min));
  }

  @Override
  public Response<Set<byte[]>> zrevrangeByLex(
      byte[] key, byte[] max, byte[] min, int offset, int count) {
    String command = "zrevrangeByLex";
    return instrumented(command, () -> delegated.zrevrangeByLex(key, max, min, offset, count));
  }

  @Override
  public Response<Set<String>> zrevrangeByLex(
      String key, String max, String min, int offset, int count) {
    String command = "zrevrangeByLex";
    return instrumented(command, () -> delegated.zrevrangeByLex(key, max, min, offset, count));
  }

  @Override
  public Response<Long> zremrangeByLex(byte[] key, byte[] min, byte[] max) {
    String command = "zremrangeByLex";
    return instrumented(command, () -> delegated.zremrangeByLex(key, min, max));
  }

  @Override
  public Response<Long> zremrangeByLex(String key, String min, String max) {
    String command = "zremrangeByLex";
    return instrumented(command, () -> delegated.zremrangeByLex(key, min, max));
  }

  @Override
  public Response<Long> bitcount(String key) {
    String command = "bitcount";
    return instrumented(command, () -> delegated.bitcount(key));
  }

  @Override
  public Response<Long> bitcount(String key, long start, long end) {
    String command = "bitcount";
    return instrumented(command, () -> delegated.bitcount(key, start, end));
  }

  @Override
  public Response<Long> bitcount(byte[] key) {
    String command = "bitcount";
    return instrumented(command, () -> delegated.bitcount(key));
  }

  @Override
  public Response<Long> bitcount(byte[] key, long start, long end) {
    String command = "bitcount";
    return instrumented(command, () -> delegated.bitcount(key, start, end));
  }

  @Override
  public Response<byte[]> dump(String key) {
    String command = "dump";
    return instrumented(command, () -> delegated.dump(key));
  }

  @Override
  public Response<byte[]> dump(byte[] key) {
    String command = "dump";
    return instrumented(command, () -> delegated.dump(key));
  }

  @Override
  public Response<String> migrate(
      String host, int port, String key, int destinationDb, int timeout) {
    String command = "migrate";
    return instrumented(command, () -> delegated.migrate(host, port, key, destinationDb, timeout));
  }

  @Override
  public Response<String> migrate(
      String host, int port, byte[] key, int destinationDb, int timeout) {
    String command = "migrate";
    return instrumented(command, () -> delegated.migrate(host, port, key, destinationDb, timeout));
  }

  @Override
  public Response<Long> objectRefcount(String key) {
    String command = "objectRefcount";
    return instrumented(command, () -> delegated.objectRefcount(key));
  }

  @Override
  public Response<Long> objectRefcount(byte[] key) {
    String command = "objectRefcount";
    return instrumented(command, () -> delegated.objectRefcount(key));
  }

  @Override
  public Response<String> objectEncoding(String key) {
    String command = "objectEncoding";
    return instrumented(command, () -> delegated.objectEncoding(key));
  }

  @Override
  public Response<byte[]> objectEncoding(byte[] key) {
    String command = "objectEncoding";
    return instrumented(command, () -> delegated.objectEncoding(key));
  }

  @Override
  public Response<Long> objectIdletime(String key) {
    String command = "objectIdletime";
    return instrumented(command, () -> delegated.objectIdletime(key));
  }

  @Override
  public Response<Long> objectIdletime(byte[] key) {
    String command = "objectIdletime";
    return instrumented(command, () -> delegated.objectIdletime(key));
  }

  @Override
  public Response<Long> pexpire(String key, long milliseconds) {
    String command = "pexpire";
    return instrumented(command, () -> delegated.pexpire(key, milliseconds));
  }

  @Override
  public Response<Long> pexpire(byte[] key, long milliseconds) {
    String command = "pexpire";
    return instrumented(command, () -> delegated.pexpire(key, milliseconds));
  }

  @Override
  public Response<Long> pexpireAt(String key, long millisecondsTimestamp) {
    String command = "pexpireAt";
    return instrumented(command, () -> delegated.pexpireAt(key, millisecondsTimestamp));
  }

  @Override
  public Response<Long> pexpireAt(byte[] key, long millisecondsTimestamp) {
    String command = "pexpireAt";
    return instrumented(command, () -> delegated.pexpireAt(key, millisecondsTimestamp));
  }

  @Override
  public Response<Long> pttl(String key) {
    String command = "pttl";
    return instrumented(command, () -> delegated.pttl(key));
  }

  @Override
  public Response<Long> pttl(byte[] key) {
    String command = "pttl";
    return instrumented(command, () -> delegated.pttl(key));
  }

  @Override
  public Response<String> restore(String key, int ttl, byte[] serializedValue) {
    String command = "restore";
    return instrumented(command, () -> delegated.restore(key, ttl, serializedValue));
  }

  @Override
  public Response<String> restore(byte[] key, int ttl, byte[] serializedValue) {
    String command = "restore";
    return instrumented(command, () -> delegated.restore(key, ttl, serializedValue));
  }

  @Override
  public Response<Double> incrByFloat(String key, double increment) {
    String command = "incrByFloat";
    return instrumented(command, () -> delegated.incrByFloat(key, increment));
  }

  @Override
  public Response<Double> incrByFloat(byte[] key, double increment) {
    String command = "incrByFloat";
    return instrumented(command, () -> delegated.incrByFloat(key, increment));
  }

  @Override
  public Response<String> psetex(String key, long milliseconds, String value) {
    String command = "psetex";
    return instrumented(
        command, payloadSize(value), () -> delegated.psetex(key, milliseconds, value));
  }

  @Override
  public Response<String> psetex(byte[] key, long milliseconds, byte[] value) {
    String command = "psetex";
    return instrumented(
        command, payloadSize(value), () -> delegated.psetex(key, milliseconds, value));
  }

  @Override
  public Response<String> set(String key, String value, SetParams params) {
    String command = "set";
    return instrumented(command, payloadSize(value), () -> delegated.set(key, value, params));
  }

  @Override
  public Response<String> set(byte[] key, byte[] value, SetParams params) {
    String command = "set";
    return instrumented(command, payloadSize(value), () -> delegated.set(key, value, params));
  }

  @Override
  public Response<Double> hincrByFloat(String key, String field, double increment) {
    String command = "hincrByFloat";
    return instrumented(command, () -> delegated.hincrByFloat(key, field, increment));
  }

  @Override
  public Response<Double> hincrByFloat(byte[] key, byte[] field, double increment) {
    String command = "hincrByFloat";
    return instrumented(command, () -> delegated.hincrByFloat(key, field, increment));
  }

  @Override
  public Response<Object> eval(String script) {
    String command = "eval";
    return instrumented(command, payloadSize(script), () -> delegated.eval(script));
  }

  @Override
  public Response<Object> eval(String script, List<String> keys, List<String> args) {
    String command = "eval";
    return instrumented(
        command, payloadSize(script) + payloadSize(args), () -> delegated.eval(script, keys, args));
  }

  @Override
  public Response<Object> eval(String script, int numKeys, String... args) {
    String command = "eval";
    return instrumented(
        command,
        payloadSize(script) + payloadSize(args),
        () -> delegated.eval(script, numKeys, args));
  }

  @Override
  public Response<Object> evalsha(String script) {
    String command = "evalsha";
    return instrumented(command, payloadSize(script), () -> delegated.evalsha(script));
  }

  @Override
  public Response<Object> evalsha(String sha1, List<String> keys, List<String> args) {
    String command = "evalsha";
    return instrumented(command, payloadSize(args), () -> delegated.evalsha(sha1, keys, args));
  }

  @Override
  public Response<Object> evalsha(String sha1, int numKeys, String... args) {
    String command = "evalsha";
    return instrumented(command, payloadSize(args), () -> delegated.evalsha(sha1, numKeys, args));
  }

  @Override
  public Response<Long> pfadd(byte[] key, byte[]... elements) {
    String command = "pfadd";
    return instrumented(command, payloadSize(elements), () -> delegated.pfadd(key, elements));
  }

  @Override
  public Response<Long> pfcount(byte[] key) {
    String command = "pfcount";
    return instrumented(command, () -> delegated.pfcount(key));
  }

  @Override
  public Response<Long> pfadd(String key, String... elements) {
    String command = "pfadd";
    return instrumented(command, payloadSize(elements), () -> delegated.pfadd(key, elements));
  }

  @Override
  public Response<Long> pfcount(String key) {
    String command = "pfcount";
    return instrumented(command, () -> delegated.pfcount(key));
  }

  @Override
  public Response<Long> geoadd(byte[] key, double longitude, double latitude, byte[] member) {
    String command = "geoadd";
    return instrumented(command, () -> delegated.geoadd(key, longitude, latitude, member));
  }

  @Override
  public Response<Long> geoadd(byte[] key, Map<byte[], GeoCoordinate> memberCoordinateMap) {
    String command = "geoadd";
    return instrumented(command, () -> delegated.geoadd(key, memberCoordinateMap));
  }

  @Override
  public Response<Long> geoadd(String key, double longitude, double latitude, String member) {
    String command = "geoadd";
    return instrumented(command, () -> delegated.geoadd(key, longitude, latitude, member));
  }

  @Override
  public Response<Long> geoadd(String key, Map<String, GeoCoordinate> memberCoordinateMap) {
    String command = "geoadd";
    return instrumented(command, () -> delegated.geoadd(key, memberCoordinateMap));
  }

  @Override
  public Response<Double> geodist(byte[] key, byte[] member1, byte[] member2) {
    String command = "geodist";
    return instrumented(command, () -> delegated.geodist(key, member1, member2));
  }

  @Override
  public Response<Double> geodist(byte[] key, byte[] member1, byte[] member2, GeoUnit unit) {
    String command = "geodist";
    return instrumented(command, () -> delegated.geodist(key, member1, member2, unit));
  }

  @Override
  public Response<Double> geodist(String key, String member1, String member2) {
    String command = "geodist";
    return instrumented(command, () -> delegated.geodist(key, member1, member2));
  }

  @Override
  public Response<Double> geodist(String key, String member1, String member2, GeoUnit unit) {
    String command = "geodist";
    return instrumented(command, () -> delegated.geodist(key, member1, member2, unit));
  }

  @Override
  public Response<List<byte[]>> geohash(byte[] key, byte[]... members) {
    String command = "geohash";
    return instrumented(command, () -> delegated.geohash(key, members));
  }

  @Override
  public Response<List<String>> geohash(String key, String... members) {
    String command = "geohash";
    return instrumented(command, () -> delegated.geohash(key, members));
  }

  @Override
  public Response<List<GeoCoordinate>> geopos(byte[] key, byte[]... members) {
    String command = "geopos";
    return instrumented(command, () -> delegated.geopos(key, members));
  }

  @Override
  public Response<List<GeoCoordinate>> geopos(String key, String... members) {
    String command = "geopos";
    return instrumented(command, () -> delegated.geopos(key, members));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadius(
      byte[] key, double longitude, double latitude, double radius, GeoUnit unit) {
    String command = "georadius";
    return instrumented(command, () -> delegated.georadius(key, longitude, latitude, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadius(
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
  public Response<List<GeoRadiusResponse>> georadius(
      String key, double longitude, double latitude, double radius, GeoUnit unit) {
    String command = "georadius";
    return instrumented(command, () -> delegated.georadius(key, longitude, latitude, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadius(
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
  public Response<List<GeoRadiusResponse>> georadiusByMember(
      byte[] key, byte[] member, double radius, GeoUnit unit) {
    String command = "georadiusByMember";
    return instrumented(command, () -> delegated.georadiusByMember(key, member, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMember(
      byte[] key, byte[] member, double radius, GeoUnit unit, GeoRadiusParam param) {
    String command = "georadiusByMember";
    return instrumented(
        command, () -> delegated.georadiusByMember(key, member, radius, unit, param));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMember(
      String key, String member, double radius, GeoUnit unit) {
    String command = "georadiusByMember";
    return instrumented(command, () -> delegated.georadiusByMember(key, member, radius, unit));
  }

  @Override
  public Response<List<GeoRadiusResponse>> georadiusByMember(
      String key, String member, double radius, GeoUnit unit, GeoRadiusParam param) {
    String command = "georadiusByMember";
    return instrumented(
        command, () -> delegated.georadiusByMember(key, member, radius, unit, param));
  }

  @Override
  public Response<List<Long>> bitfield(String key, String... elements) {
    String command = "bitfield";
    return instrumented(command, () -> delegated.bitfield(key, elements));
  }

  @Override
  public Response<List<Long>> bitfield(byte[] key, byte[]... elements) {
    String command = "bitfield";
    return instrumented(command, () -> delegated.bitfield(key, elements));
  }
}

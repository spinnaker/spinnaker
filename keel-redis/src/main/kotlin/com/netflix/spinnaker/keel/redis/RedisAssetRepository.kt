package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetBase
import com.netflix.spinnaker.keel.model.AssetContainer
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.model.TypedByteArray
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisCommands
import java.time.Clock
import java.time.Instant
import java.util.*
import javax.annotation.PostConstruct

class RedisAssetRepository(
  private val redisClient: RedisClientDelegate,
  private val clock: Clock
) : AssetRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun rootAssets(callback: (Asset) -> Unit) {
    withRedis { redis ->
      redis.smembers(INDEX_SET)
        .asSequence()
        .map(::AssetId)
        .filter { redis.scard(it.dependsOnKey) == 0L }
        .forEach { id ->
          readAsset(redis, id)
            ?.also { (it as? Asset)?.also(callback) }
            ?: onInvalidIndex(id)
        }
    }
  }

  override fun allAssets(callback: (AssetBase) -> Unit) {
    withRedis { redis ->
      redis.smembers(INDEX_SET)
        .map(::AssetId)
        .forEach { id ->
          readAsset(redis, id)
            ?.also(callback)
            ?: onInvalidIndex(id)
        }
    }
  }

  override fun get(id: AssetId): Asset? =
    withRedis { redis ->
      readAsset(redis, id) as? Asset
    }

  override fun getPartial(id: AssetId): PartialAsset? =
    withRedis { redis ->
      readAsset(redis, id) as? PartialAsset
    }

  override fun getContainer(id: AssetId): AssetContainer? =
    withRedis { redis ->
      val root = readAsset(redis, id) as? Asset
      if (root != null) {
        val partials = redis
          .smembers(id.partialsKey)
          .asSequence()
          // TODO: optimally this should be done with a single read operation
          .map { readAsset(redis, it.let(::AssetId)) as? PartialAsset }
          .filterNotNull()
          .toSet()
        AssetContainer(root, partials)
      } else {
        null
      }
    }

  override fun store(asset: AssetBase) {
    withRedis { redis ->
      redis.hmset(asset.id.key, asset.toHash())
      if (asset is Asset && asset.dependsOn.isNotEmpty()) {
        redis.sadd(asset.id.dependsOnKey, *asset.dependsOn.map { it.value }.toTypedArray())
        asset.dependsOn.forEach {
          redis.sadd(it.dependenciesKey, asset.id.value)
        }
      } else if (asset is PartialAsset) {
        redis.sadd(
          asset.root.partialsKey,
          asset.id.value
        )
      }
      redis.sadd(INDEX_SET, asset.id.value)
      redis.zadd(asset.id.stateKey, timestamp(), AssetState.Unknown.name)
    }
  }

  override fun delete(id: AssetId) {
    withRedis { redis ->
      redis.del(id.key)
      redis.smembers(id.dependsOnKey).forEach {
        redis.srem(AssetId(it).dependenciesKey, id.value)
      }
      redis.del(id.dependsOnKey)
      redis.srem(INDEX_SET, id.value)
        // TODO: partials key
    }
  }

  override fun dependents(id: AssetId): Iterable<AssetId> =
    withRedis { redis ->
      redis.smembers(id.dependenciesKey).map(::AssetId)
    }

  override fun lastKnownState(id: AssetId): Pair<AssetState, Instant>? =
    withRedis { redis ->
      redis.zrangeByScoreWithScores(id.stateKey, Double.MIN_VALUE, Double.MAX_VALUE, 0, 1)
        .asSequence()
        .map { AssetState.valueOf(it.element) to Instant.ofEpochMilli(it.score.toLong()) }
        .firstOrNull()
    }

  override fun updateState(id: AssetId, state: AssetState) {
    withRedis { redis ->
      redis.zadd(id.stateKey, timestamp(), state.name)
    }
  }

  @PostConstruct
  fun logKnownAssets() {
    withRedis { redis ->
      redis
        .smembers(INDEX_SET)
        .sorted()
        .also { log.info("Managing the following assets:") }
        .forEach { log.info(" - $it") }
    }
  }

  companion object {
    private const val INDEX_SET = "keel.assets"
    private const val ASSET_HASH = "{keel.asset.%s}"
    private const val DEPENDENCIES_SET = "$ASSET_HASH.dependencies"
    private const val DEPENDS_ON_SET = "$ASSET_HASH.dependsOn"
    private const val PARTIALS_SET = "$ASSET_HASH.partials"
    private const val STATE_SORTED_SET = "$ASSET_HASH.state"
  }

  private fun readAsset(redis: JedisCommands, id: AssetId): AssetBase? =
    if (redis.sismember(INDEX_SET, id.value)) {
      redis.hgetAll(id.key)?.let {
        if (it.getValue("@class") == Asset::class.qualifiedName) {
          Asset(
            id,
            it.getValue("apiVersion"),
            it.getValue("kind"),
            redis.smembers(id.dependsOnKey).asSequence().map(::AssetId).toSet(),
            TypedByteArray(
              it.getValue("spec.type"),
              it.getValue("spec.data").decodeBase64()
            )
          )
        } else {
          PartialAsset(
            id,
            it.getValue("root").let(::AssetId),
            it.getValue("apiVersion"),
            it.getValue("kind"),
            TypedByteArray(
              it.getValue("spec.type"),
              it.getValue("spec.data").decodeBase64()
            )
          )
        }
      }
    } else {
      null
    }

  private fun onInvalidIndex(id: AssetId) {
    log.error("Invalid index entry {}", id)
    withRedis { redis -> redis.srem(INDEX_SET, id.value) }
  }

  private fun <T> withRedis(operation: (JedisCommands) -> T): T =
    redisClient.withCommandsClient(operation)

  private val AssetId.key: String
    get() = ASSET_HASH.format(value)

  private val AssetId.dependenciesKey: String
    get() = DEPENDENCIES_SET.format(value)

  private val AssetId.dependsOnKey: String
    get() = DEPENDS_ON_SET.format(value)

  private val AssetId.partialsKey: String
    get() = PARTIALS_SET.format(value)

  private val AssetId.stateKey: String
    get() = STATE_SORTED_SET.format(value)

  private fun AssetBase.toHash(): Map<String, String> = mutableMapOf(
    "@class" to javaClass.name,
    "apiVersion" to apiVersion,
    "kind" to kind,
    "spec.type" to spec.type,
    "spec.data" to spec.data.encodeBase64()
  ).also {
    if (this is PartialAsset) {
      it["root"] = root.value
    }
  }

  private fun timestamp() = clock.millis().toDouble()

  private fun ByteArray.encodeBase64() =
    String(Base64.getEncoder().encode(this))

  private fun String.decodeBase64() =
    Base64.getDecoder().decode(this)
}

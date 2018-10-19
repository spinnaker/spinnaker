package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetBase
import com.netflix.spinnaker.keel.model.AssetContainer
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.model.TypedByteArray
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisCommands
import java.time.Instant
import java.util.*
import javax.annotation.PostConstruct

class RedisAssetRepository(
  private val redisClient: JedisClientDelegate
) : AssetRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun rootAssets(callback: (Asset) -> Unit) {
    withRedis { redis ->
      redis.smembers(INDEX_SET)
        .map(::AssetId)
        .forEach { id ->
          if (redis.scard(id.dependsOnKey) == 0L) {
            readAsset(redis, id)
              ?.also { (it as? Asset)?.also(callback) }
              ?: onInvalidIndex(id)
          }
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

  override fun getPartial(id: AssetId): PartialAsset? {
    TODO("not implemented")
  }

  override fun getContainer(id: AssetId): AssetContainer? {
    TODO("not implemented")
  }

  override fun store(asset: AssetBase) {
    withRedis { redis ->
      redis.hmset(asset.key, asset.toHash())
      if (asset is Asset && asset.dependsOn.isNotEmpty()) {
        redis.sadd(
          asset.dependsOnKey,
          *asset.dependsOn.map { it.value }.toTypedArray()
        )
      }
      redis.sadd(INDEX_SET, asset.id.value)
    }
  }

  override fun dependents(id: AssetId): Iterable<AssetId> {
    TODO("not implemented")
  }

  override fun lastKnownState(id: AssetId): Pair<AssetState, Instant>? {
    TODO("not implemented")
  }

  override fun updateState(id: AssetId, state: AssetState) {
    TODO("not implemented")
  }

  @PostConstruct
  fun logKnownPlugins() {
    withRedis { redis ->
    }
  }

  companion object {
    private const val INDEX_SET = "keel:assets"
    private const val ASSET_HASH = "keel:asset:%s"
    private const val DEPENDS_ON_SET = "keel:asset:%s:dependsOn"
  }

  private fun readAsset(redis: JedisCommands, id: AssetId): AssetBase? {
    return redis.hgetAll(id.key)?.let {
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
  }

  private fun onInvalidIndex(id: AssetId) {
    log.error("Invalid index entry {}", id)
    withRedis { redis -> redis.srem(INDEX_SET, id.value) }
  }

  private fun <T> withRedis(operation: (JedisCommands) -> T): T =
    redisClient.withCommandsClient(operation)

  private val AssetId.key: String
    get() = ASSET_HASH.format(value)

  private val AssetBase.key: String
    get() = id.key

  private val AssetId.dependsOnKey: String
    get() = DEPENDS_ON_SET.format(value)

  private val AssetBase.dependsOnKey: String
    get() = id.dependsOnKey

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

  private fun ByteArray.encodeBase64() =
    String(Base64.getEncoder().encode(this))

  private fun String.decodeBase64() =
    Base64.getDecoder().decode(this)
}

package com.netflix.spinnaker.keel.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.persistence.ArtifactAlreadyRegistered
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import redis.clients.jedis.JedisCommands

class RedisArtifactRepository(
  private val redisClient: RedisClientDelegate,
  private val objectMapper: ObjectMapper
) : ArtifactRepository {
  override fun register(artifact: DeliveryArtifact) {
    redisClient.withCommandsClient<Unit> { redis ->
      redis.sadd("keel.delivery_artifacts", artifact.asJson())
        .also { count ->
          if (count == 0L) throw ArtifactAlreadyRegistered(artifact)
        }
    }
  }

  override fun store(artifact: DeliveryArtifact, version: String): Boolean =
    redisClient.withCommandsClient<Boolean> { redis ->
      if (!redis.isRegistered(artifact)) {
        throw NoSuchArtifactException(artifact)
      }
      redis.sadd(artifact.versionsKey, version) > 0
    }

  override fun isRegistered(name: String, type: ArtifactType): Boolean =
    redisClient.withCommandsClient<Boolean> { redis ->
      redis.isRegistered(DeliveryArtifact(name, type))
    }

  override fun versions(artifact: DeliveryArtifact): List<String> =
    redisClient.withCommandsClient<List<String>> { redis ->
      if (!redis.isRegistered(artifact)) {
        throw NoSuchArtifactException(artifact)
      }
      redis.smembers(artifact.versionsKey)
        .sortedDescending()
    }

  private fun JedisCommands.isRegistered(artifact: DeliveryArtifact) =
    sismember("keel.delivery_artifacts", artifact.asJson())

  private fun DeliveryArtifact.asJson() =
    objectMapper.writeValueAsString(this)

  private val DeliveryArtifact.versionsKey: String
    get() = "{keel.delivery_artifact_versions.%s.%s}".format(type, name)
}

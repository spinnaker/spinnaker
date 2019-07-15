package com.netflix.spinnaker.orca.q.pending

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.DualPendingExecutionServiceConfiguration
import com.netflix.spinnaker.q.Message
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Intended for migrating between PendingExecutionService implementations.
 *
 * Reads always favor the previous class, falling back to the primary when previous has no pending
 * messages for a pipelineConfigId. Writes are only performed to the primary class.
 *
 * The `queue.pending.previous.pop` counter metric can be used to track if the previous class is still
 * occasionally returning pending messages. Because the RedisPendingExecutionService class stores
 * pending messages in a set per pipelineConfigId without indexing which pipelineConfigIds have pending
 * messages, getting an overall snapshot of migration state direct from redis is deemed overly expensive.
 *
 * Example orca.yml configuration leveraging this class to migrate from RedisPendingExecutionService
 * to SqlPendingExecutionService:
 *
 * ```
 * queue:
 *   pendingExecutionService:
 *   sql.enabled: true
 *   redis.enabled: true
 *   dual:
 *     enabled: true
 *     primaryClass: com.netflix.spinnaker.orca.q.sql.pending.SqlPendingExecutionService
 *     previousClass: com.netflix.spinnaker.orca.q.redis.pending.RedisPendingExecutionService
 * ```
 */
@Primary
@Component
@ConditionalOnProperty(value = ["queue.pending-execution-service.dual.enabled"])
@EnableConfigurationProperties(DualPendingExecutionServiceConfiguration::class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class DualPendingExecutionService(
  config: DualPendingExecutionServiceConfiguration,
  allPendingServices: List<PendingExecutionService>,
  private val registry: Registry
) : PendingExecutionService {

  private val log = LoggerFactory.getLogger(javaClass)

  lateinit var primary: PendingExecutionService
  lateinit var previous: PendingExecutionService

  private final var hitFromSecondaryId = registry.createId("queue.pending.previous.pop")

  init {
    allPendingServices.forEach {
      log.info("Available PendingExecutionServices: $it")
    }

    primary = allPendingServices.first { it.javaClass.name == config.primaryClass }
    previous = allPendingServices.first { it.javaClass.name == config.previousClass }
  }

  override fun enqueue(pipelineConfigId: String, message: Message) =
    primary.enqueue(pipelineConfigId, message)

  override fun popOldest(pipelineConfigId: String): Message? {
    val message = previous.popOldest(pipelineConfigId)
    return if (message != null) {
      registry.counter(hitFromSecondaryId).increment()
      message
    } else {
      primary.popOldest(pipelineConfigId)
    }
  }

  override fun popNewest(pipelineConfigId: String): Message? {
    val message = previous.popNewest(pipelineConfigId)
    return if (message != null) {
      registry.counter(hitFromSecondaryId).increment()
      message
    } else {
      primary.popNewest(pipelineConfigId)
    }
  }

  override fun purge(pipelineConfigId: String, callback: (Message) -> Unit) {
    do {
      val oldest = popOldest(pipelineConfigId)
      oldest?.let(callback)
    } while (oldest != null)
  }

  override fun depth(pipelineConfigId: String): Int {
    return previous.depth(pipelineConfigId) + primary.depth(pipelineConfigId)
  }
}

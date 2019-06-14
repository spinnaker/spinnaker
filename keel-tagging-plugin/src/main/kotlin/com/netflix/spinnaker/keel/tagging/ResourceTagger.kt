/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.keel.tagging

import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.events.DeleteEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.tags.EntityRef
import com.netflix.spinnaker.keel.tags.EntityTag
import com.netflix.spinnaker.keel.tags.KEEL_TAG_NAME
import com.netflix.spinnaker.keel.tags.TagValue
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * A scheduled job that checks to make sure each resource keel is managing
 * has an entity tag indicating this, as well as linking the entity tag id
 * to the keel id.
 *
 */
class ResourceTagger(
  private val resourceRepository: ResourceRepository,
  private val resourcePersister: ResourcePersister,
  private val cloudDriverService: CloudDriverService,
  private val publisher: ApplicationEventPublisher
) {
  private val log = LoggerFactory.getLogger(javaClass)

  private var enabled = false
  private var accounts: Set<Credential> = emptySet()
  private var accountsUpdateTimeS = 0L
  private var accountsUpdateFrequencyS = TimeUnit.MINUTES.toSeconds(10)
  private val transforms = mapOf(
    "ec2" to "aws"
  )

  init {
    syncAccounts()
  }

  @EventListener(ApplicationUp::class)
  fun onApplicationUp() {
    log.info("Application up, enabling scheduled resource tagging")
    enabled = true
  }

  @EventListener(ApplicationDown::class)
  fun onApplicationDown() {
    log.info("Application down, disabling scheduled resource tagging")
    enabled = false
  }

  @Scheduled(fixedDelayString = "\${keel.resource-tagger.frequency:PT10S}")
  fun checkResources() {
    if (enabled) {
      publisher.publishEvent(ScheduledResourceTaggingCheckStarting)
      log.debug("Starting scheduled resource tagging…")
      syncAccounts()
      // todo emjburns: maybe one instance shouldn't do this whole thing
      resourceRepository
        .allResources { resourceHeader ->
          if (resourceHeader.apiVersion != SPINNAKER_API_V1.subApi("tag")) {
            try {
              val tagResource = resourceRepository.get(resourceHeader.name.toTagSpecName(), KeelTagSpec::class.java)
              if (tagResource.spec.tagState is TagNotDesired) {
                // if it's in the resource repository, we want it to be tagged.
                persistTagState(resourceHeader.name.generateKeelTagSpec())
              }
            } catch (e: NoSuchResourceException) {
              persistTagState(resourceHeader.name.generateKeelTagSpec())
            }
          }
        }
      log.debug("Scheduled tagging complete")
    } else {
      log.debug("Scheduled tagging disabled")
    }
  }

  // todo emjburns: should there be a catchup job for deletes?
  @EventListener(DeleteEvent::class)
  fun onDeleteEvent(event: DeleteEvent) {
    log.debug("Persisting no tag desired for resource {} because it is no longer managed", event.resourceName.toString())
    val spec = KeelTagSpec(
      keelId = event.resourceName.toString(),
      entityRef = event.resourceName.toEntityRef(),
      tagState = TagNotDesired()
    )
    persistTagState(spec)
  }

  private fun persistTagState(spec: KeelTagSpec) {
    log.debug("Persisting tag desired state for resource {}", spec.keelId)
    val submitted = spec.toSubmittedResource()
    val name = ResourceName(spec.generateTagNameFromKeelId())

    if (tagExists(name)) {
      resourcePersister.update(name, submitted)
    } else {
      resourcePersister.create(submitted)
    }
  }

  private fun tagExists(tagResourceName: ResourceName): Boolean {
    try {
      resourceRepository.get(tagResourceName, KeelTagSpec::class.java)
      return true
    } catch (e: NoSuchResourceException) {
      return false
    }
  }

  private fun KeelTagSpec.generateTagNameFromKeelId() = "tag:keel-tag:$keelId"

  private fun KeelTagSpec.toSubmittedResource() =
    SubmittedResource(
      apiVersion = SPINNAKER_API_V1.subApi("tag"),
      kind = "keel-tag",
      spec = this
    ) as SubmittedResource<Any>

  private fun ResourceName.generateKeelTagSpec() =
    KeelTagSpec(
      toString(),
      toEntityRef(),
      generateTagDesired()
    )

  fun ResourceName.generateTagDesired() =
    TagDesired(tag = EntityTag(
      value = TagValue(
        message = KEEL_TAG_MESSAGE,
        keelResourceId = toString(),
        type = "notice"
      ),
      namespace = KEEL_TAG_NAMESPACE,
      valueType = "object",
      category = "notice",
      name = KEEL_TAG_NAME
    )
    )

  private fun syncAccounts() {
    val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
    if (now - accountsUpdateFrequencyS > accountsUpdateTimeS) {
      log.info("Refreshing clouddriver accounts")
      runBlocking { accounts = cloudDriverService.listCredentials() }
      accountsUpdateTimeS = now
    }
  }

  private fun ResourceName.toTagSpecName() = ResourceName("tag:keel-tag:$this")

  private fun ResourceName.toEntityRef(): EntityRef {
    val (pluginGroup, resourceType, account, region, resourceId) = toString().split(":")
    val accountId = try {
      val fullAccount = accounts.first { a -> a.name == account }
      fullAccount.attributes.getOrDefault("accountId", account).toString()
    } catch (e: NoSuchElementException) {
      log.error("Can't find $account in list of Clouddriver accounts. Valid options: {}", account, accounts)
      account
    }

    return EntityRef(
      entityType = resourceType,
      entityId = resourceId,
      application = resourceId.substringBefore("-"),
      region = region,
      account = accountId,
      cloudProvider = transforms.getOrDefault(pluginGroup, pluginGroup)
    )
  }
}

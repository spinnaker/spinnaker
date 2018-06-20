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
package com.netflix.spinnaker.keel.asset.aws.securitygroup

import com.netflix.spinnaker.keel.AssetRepository
import com.netflix.spinnaker.keel.AssetStatus
import com.netflix.spinnaker.keel.PARENT_ASSET_LABEL
import com.netflix.spinnaker.keel.attribute.EnabledAttribute
import com.netflix.spinnaker.keel.event.AssetAwareEvent
import com.netflix.spinnaker.keel.event.BeforeAssetDryRunEvent
import com.netflix.spinnaker.keel.event.BeforeAssetScheduleEvent
import com.netflix.spinnaker.keel.event.BeforeAssetUpsertEvent
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AmazonSecurityGroupAssetListener(
  private val assetRepository: AssetRepository
) {

  private val log = LoggerFactory.getLogger(AmazonSecurityGroupAssetListener::class.java)

  /**
   * Adds an internal label on the asset linking security group rule assets
   * to their parent asset, even if the parent does not exist yet.
   */
  @EventListener(BeforeAssetUpsertEvent::class, BeforeAssetDryRunEvent::class)
  fun assignParentAssetLabel(event: AssetAwareEvent) {
    val asset = event.asset as? AmazonSecurityGroupAsset ?: return
    asset.parentId()
      ?.also { asset.labels[PARENT_ASSET_LABEL] = it }
  }

  /**
   * Due to how [AmazonSecurityGroupAssetProcessor] converges security groups
   * and individual rules, we shouldn't allow Security Group Rule Assets to be
   * converged on their own by the scheduler.
   *
   * This should only happen when Keel has an _active_ asset for the root SG.
   */
  @EventListener(BeforeAssetScheduleEvent::class)
  fun disableRuleAssetsOnScheduledConverge(event: BeforeAssetScheduleEvent) {
    val asset = event.asset as? AmazonSecurityGroupAsset ?: return
    if (event.asset.spec !is AmazonSecurityGroupRuleSpec) {
      return
    }

    val parentId = asset.parentId()
    if (parentId == null) {
      // This is bad, but we don't need to throw an exception or anything here.
      // Just process the asset. It's unnecessary overhead, but not the end
      // of the world.
      log.error("AmazonSecurityGroupAsset is a rule, but no parentId defined", kv("assetId", asset.id()))
      return
    }

    val rootAsset = assetRepository.getAsset(parentId)
    if (rootAsset == null || rootAsset.status != AssetStatus.ACTIVE) {
      return
    }

    if (asset.hasAttribute(EnabledAttribute::class)) {
      asset.attributes.removeIf { it is EnabledAttribute }
    }
    asset.attributes.add(EnabledAttribute(false))
  }
}

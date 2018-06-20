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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.asset.DefaultConvertToJobCommand
import com.netflix.spinnaker.keel.asset.NamedReferenceSupport
import com.netflix.spinnaker.keel.asset.SecurityGroupSpec
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.exceptions.DeclarativeException
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.state.StateInspector
import org.springframework.stereotype.Component

/**
 * Handles both full security group assets, as well as individual security
 * group rules.
 *
 * Externally-defined security group rules can be created against a root
 * asset, or a security group that isn't managed declaratively. This allows
 * different organizations to modify security group permissions independently
 * of each other, as well as incrementally convert to asset-based permissions.
 *
 * TODO rz - Root assets should be able to provide policies of who (or what)
 * can actually create rules against it, if anyone. By default, access will be
 * left open.
 */
@Component
class AmazonSecurityGroupAssetProcessor(
  private val assetRepository: AssetRepository,
  private val objectMapper: ObjectMapper,
  private val converter: AmazonSecurityGroupConverter,
  private val loader: AmazonSecurityGroupLoader
) : AssetProcessor<AmazonSecurityGroupAsset> {

  override fun supports(asset: Asset<AssetSpec>) = asset is AmazonSecurityGroupAsset

  override fun converge(asset: AmazonSecurityGroupAsset): ConvergeResult {
    val changeSummary = ChangeSummary(asset.id())
    val currentState = loader.load(asset.spec)

    // This processor handles both root security groups, as well as individual rules. If a rule is passed in, we
    // need to source the root asset (or fake it out if it doesn't exist).
    var rootAsset = getRootAsset(asset)
    if (rootAsset == null) {
      // There's no technical reason we can't create a security group from a single rule asset, but that would mean
      // the description would not be set and could not change in the future.
      if (currentState == null) {
        changeSummary.type = ChangeType.FAILED_PRECONDITIONS
        changeSummary.addMessage("Target security group does not exist, nor does a resource asset exist")
        return ConvergeResult(listOf(), changeSummary)
      }

      val transientSpec = converter.convertFromState(currentState)
        ?: throw DeclarativeException("Spec converted from current state was null for asset: ${asset.id()}")

      rootAsset = AmazonSecurityGroupAsset(transientSpec)
    }

    val desiredRootAsset = mergeRootAsset(rootAsset, asset)

    if (currentStateUpToDate(asset.id(), currentState, desiredRootAsset.spec, changeSummary)) {
      changeSummary.addMessage(ConvergeReason.UNCHANGED.reason)
      return ConvergeResult(listOf(), changeSummary)
    }

    val missingGroups = missingUpstreamGroups(desiredRootAsset.spec)
    if (missingGroups.isNotEmpty()) {
      changeSummary.addMessage("Some upstream security groups are missing: $missingGroups")
      changeSummary.type = ChangeType.FAILED_PRECONDITIONS
      return ConvergeResult(listOf(), changeSummary)
    }

    changeSummary.type = if (currentState == null) ChangeType.CREATE else ChangeType.UPDATE

    return ConvergeResult(listOf(
      OrchestrationRequest(
        name = "Upsert security group",
        application = asset.spec.application,
        description = "Converging on desired state for ${asset.id()}",
        job = converter.convertToJob(DefaultConvertToJobCommand(desiredRootAsset.spec), changeSummary),
        trigger = OrchestrationTrigger(asset.id())
      )
    ), changeSummary)
  }

  /**
   * Finds all assets that have a RuleSpec with a parent ID matching the root asset, and merges the assets into
   * the root asset for the duration of the convergence.
   */
  private fun mergeRootAsset(rootAsset: AmazonSecurityGroupAsset, thisAsset: AmazonSecurityGroupAsset): AmazonSecurityGroupAsset {
    val childRules = getChildRules(rootAsset.id())

    rootAsset.spec.inboundRules.addAll(childRules.map { it.spec.inboundRules }.flatten())
    if (thisAsset.spec is AmazonSecurityGroupRuleSpec) {
      rootAsset.spec.inboundRules.addAll(thisAsset.spec.inboundRules)
    }

    // TODO rz - outbound rules

    return rootAsset
  }

  private fun getRootAsset(asset: AmazonSecurityGroupAsset): AmazonSecurityGroupAsset? {
    val parentId = asset.parentId() ?: return asset

    val root = assetRepository.getAsset(parentId)
    if (root != null) {
      if (root !is AmazonSecurityGroupAsset) {
        throw DeclarativeException("Resolved root asset is not an AmazonSecurityGroupAsset: ${root.kind}")
      }
      return root
    }
    return null
  }

  private fun currentStateUpToDate(assetId: String,
                                   currentState: SecurityGroup?,
                                   desiredState: AmazonSecurityGroupSpec,
                                   changeSummary: ChangeSummary): Boolean {
    if (currentState == null) {
      return false
    }

    val diff = StateInspector(objectMapper).getDiff(
      assetId = assetId,
      currentState = currentState,
      desiredState = converter.convertToState(desiredState),
      modelClass = SecurityGroup::class,
      specClass = SecurityGroupSpec::class,
      ignoreKeys = setOf("type", "id", "moniker", "summary", "description")
    )
    changeSummary.diff = diff
    return diff.isEmpty()
  }

  private fun missingUpstreamGroups(spec: AmazonSecurityGroupSpec): List<String> {
    return spec.inboundRules
      .filterIsInstance<NamedReferenceSupport>()
      .filter {
        spec.name != it.name
      }
      .map {
        return@map if (loader.upstreamGroup(spec, it.name) == null) it.name else null
      }
      .filterNotNull()
      .distinct()
  }

  /**
   * TODO rz - Not happy with this. The asset repository needs to have a much better filter capability
   */
  private fun getChildRules(assetId: String): List<AmazonSecurityGroupAsset> =
    assetRepository.findByLabels(mapOf(PARENT_ASSET_LABEL to assetId))
      .filter { it.status == AssetStatus.ACTIVE }
      .filterIsInstance<AmazonSecurityGroupAsset>()
      .filter { it.spec is AmazonSecurityGroupRuleSpec }

}

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

package com.netflix.spinnaker.keel.asset.aws.loadbalancer

import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.asset.DefaultConvertToJobCommand
import com.netflix.spinnaker.keel.asset.LoadBalancerAsset
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.state.StateInspector
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ClassicLoadBalancerAssetProcessor
@Autowired constructor(
  private val cloudDriverService: CloudDriverService,
  private val clouddriverCache: CloudDriverCache,
  objectMapper: ObjectMapper,
  private val classicLoadBalancerConverter: ClassicLoadBalancerConverter
) : AssetProcessor<LoadBalancerAsset> {
  private val log = LoggerFactory.getLogger(javaClass)

  private val objectMapper = objectMapper.apply {
    configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
    AmazonObjectMapperConfigurer.configure(this)

    propertyNamingStrategy = null
  }

  override fun supports(asset: Asset<AssetSpec>) = asset.spec is ClassicLoadBalancerSpec

  override fun converge(asset: LoadBalancerAsset): ConvergeResult {
    val changeSummary = ChangeSummary(asset.id())
    val spec = asset.spec as ClassicLoadBalancerSpec

    log.info("Converging state for {}", value("asset", asset.id()))

    val currentLoadBalancer = getLoadBalancer(spec)

    if (currentStateUpToDate(asset.id(), currentLoadBalancer, spec, changeSummary)) {
      changeSummary.addMessage(ConvergeReason.UNCHANGED.reason)
      return ConvergeResult(listOf(), changeSummary)
    }

    changeSummary.type = if (currentLoadBalancer == null) ChangeType.CREATE else ChangeType.UPDATE

    return ConvergeResult(listOf(
        OrchestrationRequest(
          name = "Upsert Classic Load Balancer",
          application = asset.spec.application,
          description = "Converging Classic Load Balancer (${spec.accountName}:${spec.region}:${spec.name})",
          job = classicLoadBalancerConverter.convertToJob(DefaultConvertToJobCommand(spec), changeSummary),
          trigger = OrchestrationTrigger(asset.id())
        )
      ),
      changeSummary
    )
  }

  private fun currentStateUpToDate(assetId: String,
                                   currentLoadBalancer: LoadBalancerDescription?,
                                   desiredState: ClassicLoadBalancerSpec,
                                   changeSummary: ChangeSummary): Boolean {
    if (currentLoadBalancer == null) return false

    val currentState = classicLoadBalancerConverter.convertFromState(currentLoadBalancer)
    val diff = StateInspector(objectMapper).run {
      getDiff(
        assetId = assetId,
        currentState = currentState,
        desiredState = desiredState,
        modelClass = ClassicLoadBalancerSpec::class,
        specClass = ClassicLoadBalancerSpec::class,

        // modifying `availabilityZones` on an existing load balancer is not supported by clouddriver
        ignoreKeys = setOf("availabilityZones")
      )
    }
    changeSummary.diff = diff
    return diff.isEmpty()
  }

  private fun getLoadBalancer(spec: ClassicLoadBalancerSpec): LoadBalancerDescription? {
    val loadBalancers = cloudDriverService.getLoadBalancer(
      spec.cloudProvider(),
      spec.accountName,
      spec.region,
      spec.name
    )

    if (loadBalancers.isEmpty()) {
      return null
    }

    return objectMapper.convertValue(loadBalancers.get(0), LoadBalancerDescription::class.java)
  }
}

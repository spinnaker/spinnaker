/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.AppEngineOperation
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppEngineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@AppEngineOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertAppEngineLoadBalancerDescriptionValidator")
class UpsertAppEngineLoadBalancerDescriptionValidator extends DescriptionValidator<UpsertAppEngineLoadBalancerDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AppEngineClusterProvider appEngineClusterProvider

  @Override
  void validate(List priorDescriptions, UpsertAppEngineLoadBalancerDescription description, Errors errors) {
    def helper = new StandardAppEngineAttributeValidator("upsertAppEngineLoadBalancerAtomicOperationDescription", errors)

    if (!helper.validateCredentials(description.accountName, accountCredentialsProvider)) {
      return
    }

    helper.validateNotEmpty(description.loadBalancerName, "loadBalancerName")

    def trafficSplitExists = helper.validateTrafficSplit(description.split, "split")
    if (trafficSplitExists) {
      helper.validateShardBy(description.split, description.migrateTraffic, "split.shardBy")

      if (description.split.allocations) {
        def serverGroupNames = description.split.allocations.keySet()
        helper.validateServerGroupsCanBeEnabled(serverGroupNames,
                                                description.loadBalancerName,
                                                description.credentials,
                                                appEngineClusterProvider,
                                                "split.allocations")
      }

      if (description.migrateTraffic) {
        helper.validateGradualMigrationIsAllowed(description.split,
                                                 description.credentials,
                                                 appEngineClusterProvider,
                                                 "migrateTraffic")
      }
    }
  }
}

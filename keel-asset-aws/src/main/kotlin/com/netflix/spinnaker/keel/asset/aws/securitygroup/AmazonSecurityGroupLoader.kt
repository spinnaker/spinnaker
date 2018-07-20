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

import com.netflix.spinnaker.keel.asset.notFound
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.exceptions.DeclarativeException
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class AmazonSecurityGroupLoader(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache
) {

  fun load(spec: AmazonSecurityGroupSpec): SecurityGroup? {
    try {
      return cloudDriverService.getSecurityGroup(
        spec.accountName,
        "aws",
        spec.name,
        spec.region,
        spec.vpcName?.let {
          cloudDriverCache.networkBy(it, spec.accountName, spec.region).id
        }
      )
    } catch (e: RetrofitError) {
      if (e.notFound()) {
        return null
      }
      // TODO rz - need a wrapper around this guy
      throw e
    }
  }

  fun upstreamGroup(spec: AmazonSecurityGroupSpec, name: String): SecurityGroup? {
    if (spec.vpcName == null) {
      // TODO rz - This is the wrong place to do checks of this sort
      throw DeclarativeException("Only vpc security groups are supported")
    }
    try {
      return cloudDriverService.getSecurityGroup(
        spec.accountName,
        "aws",
        name,
        spec.region,
        cloudDriverCache.networkBy(
          spec.vpcName,
          spec.accountName,
          spec.region
        ).id
      )
    } catch (e: RetrofitError) {
      if (e.notFound()) {
        return null
      }
      // TODO rz - this isn't the right thing to do
      throw e
    }
  }
}

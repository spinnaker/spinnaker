/*
 * Copyright 2016 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.clouddriver.pipeline.loadbalancer.UpsertLoadBalancerStage
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

/**
 * @deprecated use {@link UpsertLoadBalancerStage} instead.
 */
@Deprecated
@Component
@CompileStatic
class UpsertAmazonLoadBalancerStage extends UpsertLoadBalancerStage {
  public static final String PIPELINE_CONFIG_TYPE = "upsertAmazonLoadBalancer"

  @Override
  String getType() {
    return PIPELINE_CONFIG_TYPE
  }
}

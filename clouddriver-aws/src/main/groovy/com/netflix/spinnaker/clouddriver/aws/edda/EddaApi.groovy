/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.edda

import com.amazonaws.services.elasticloadbalancingv2.model.Listener
import com.netflix.spinnaker.clouddriver.aws.model.edda.ApplicationLoadBalancerAttributes
import com.netflix.spinnaker.clouddriver.aws.model.edda.ClassicLoadBalancerAttributes
import com.netflix.spinnaker.clouddriver.aws.model.edda.EddaRule
import com.netflix.spinnaker.clouddriver.aws.model.edda.LoadBalancerInstanceState
import com.netflix.spinnaker.clouddriver.aws.model.edda.TargetGroupAttributes
import com.netflix.spinnaker.clouddriver.aws.model.edda.TargetGroupHealth
import retrofit.http.GET
import retrofit.http.Path

interface EddaApi {
  @GET('/REST/v2/view/loadBalancerInstances;_expand')
  List<LoadBalancerInstanceState> loadBalancerInstances()

  @GET('/REST/v2/view/targetGroupHealth;_expand')
  List<TargetGroupHealth> targetGroupHealth()

  @GET('/REST/v2/view/targetGroupAttributes;_expand')
  List<TargetGroupAttributes> targetGroupAttributes()

  @GET('/REST/v2/view/appLoadBalancerListeners/{loadBalancerName}')
  List<Listener> listeners(@Path("loadBalancerName") String loadBalancerName)

  @GET('/REST/v2/view/appLoadBalancerListeners;_expand')
  List<List<Listener>> allListeners()

  @GET('/REST/v2/view/appLoadBalancerRules/{loadBalancerName}')
  List<EddaRule> rules(@Path("loadBalancerName") String loadBalancerName)

  @GET('/REST/v2/view/appLoadBalancerRules;_expand')
  List<List<EddaRule>> allRules()

  @GET('/REST/v2/view/appLoadBalancerAttributes;_expand')
  List<ApplicationLoadBalancerAttributes> applicationLoadBalancerAttributes()

  @GET('/REST/v2/view/loadBalancerAttributes;_expand')
  List<ClassicLoadBalancerAttributes> classicLoadBalancerAttributes()
}

/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1Spec
import com.netflix.spinnaker.keel.api.ec2.old.ApplicationLoadBalancerV1_1Spec
import com.netflix.spinnaker.keel.api.ec2.old.ClusterV1Spec
import com.netflix.spinnaker.keel.api.plugins.kind

const val CLOUD_PROVIDER = "aws"

val EC2_CLUSTER_V1_1 = kind<ClusterSpec>("ec2/cluster@v1.1")

@Deprecated("Obsolete version of cluster spec", replaceWith = ReplaceWith("EC2_CLUSTER_V1_1"))
val EC2_CLUSTER_V1 = kind<ClusterV1Spec>("ec2/cluster@v1")

val EC2_SECURITY_GROUP_V1 = kind<SecurityGroupSpec>("ec2/security-group@v1")

val EC2_CLASSIC_LOAD_BALANCER_V1 = kind<ClassicLoadBalancerSpec>("ec2/classic-load-balancer@v1")

val EC2_APPLICATION_LOAD_BALANCER_V1_2 = kind<ApplicationLoadBalancerSpec>("ec2/application-load-balancer@v1.2")

@Deprecated("Obsolete version of ALB spec", replaceWith = ReplaceWith("EC2_APPLICATION_LOAD_BALANCER_V1_2"))
val EC2_APPLICATION_LOAD_BALANCER_V1_1 = kind<ApplicationLoadBalancerV1_1Spec>("ec2/application-load-balancer@v1.1")

@Deprecated("Obsolete version of ALB spec", replaceWith = ReplaceWith("EC2_APPLICATION_LOAD_BALANCER_V1_2"))
val EC2_APPLICATION_LOAD_BALANCER_V1 = kind<ApplicationLoadBalancerV1Spec>("ec2/application-load-balancer@v1")

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

package com.netflix.kato.deploy.aws

import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.AmazonRoute53Client
import com.netflix.kato.security.aws.AmazonCredentials

class StaticAmazonClients {

  static AmazonRoute53 getAmazonRoute53(AmazonCredentials credentials, String region) {
    def amazonRoute53 = new AmazonRoute53Client(credentials.credentials)
    if (region) {
      amazonRoute53.setRegion(Region.getRegion(Regions.fromName(region)))
    }
    amazonRoute53
  }

  static AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(AmazonCredentials credentials, String region) {
    def amazonElasticLoadBalancing = new AmazonElasticLoadBalancingClient(credentials.credentials)
    if (region) {
      amazonElasticLoadBalancing.setRegion(Region.getRegion(Regions.fromName(region)))
    }
    amazonElasticLoadBalancing
  }

  static AmazonEC2 getAmazonEC2(AmazonCredentials credentials, String region) {
    def amazonEc2 = new AmazonEC2Client(credentials.credentials)
    if (region) {
      amazonEc2.setRegion(Region.getRegion(Regions.fromName(region)))
    }
    amazonEc2
  }

  static AmazonAutoScaling getAutoScaling(AmazonCredentials credentials, String region) {
    def autoScaling = new AmazonAutoScalingClient(credentials.credentials)
    if (region) {
      autoScaling.setRegion(Region.getRegion(Regions.fromName(region)))
    }
    autoScaling
  }
}

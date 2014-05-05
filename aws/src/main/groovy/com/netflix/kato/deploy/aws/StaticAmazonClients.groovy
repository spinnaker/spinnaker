package com.netflix.kato.deploy.aws

import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.netflix.kato.security.aws.AmazonCredentials

class StaticAmazonClients {

  static AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(AmazonCredentials credentials, String region) {
    def amazonElasticLoadBalancing = new AmazonElasticLoadBalancingClient(credentials.credentials)
    amazonElasticLoadBalancing.setRegion(Region.getRegion(Regions.fromName(region)))
    amazonElasticLoadBalancing
  }

  static AmazonEC2 getAmazonEC2(AmazonCredentials credentials, String region) {
    def amazonEc2 = new AmazonEC2Client(credentials.credentials)
    amazonEc2.setRegion(Region.getRegion(Regions.fromName(region)))
    amazonEc2
  }

  static AmazonAutoScaling getAutoScaling(AmazonCredentials credentials, String region) {
    def autoScaling = new AmazonAutoScalingClient(credentials.credentials)
    autoScaling.setRegion(Region.getRegion(Regions.fromName(region)))
    autoScaling
  }
}

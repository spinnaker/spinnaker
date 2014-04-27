package com.netflix.kato.deploy.aws

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client

class StaticAmazonClients {
  static AmazonEC2 getAmazonEC2(String accessId, String secretKey, String region) {
    def amazonEc2 = new AmazonEC2Client(new BasicAWSCredentials(accessId, secretKey))
    amazonEc2.setRegion(Region.getRegion(Regions.fromName(region)))
    amazonEc2
  }

  static AmazonAutoScaling getAutoScaling(String accessId, String secretKey, String region) {
    def autoScaling = new AmazonAutoScalingClient(new BasicAWSCredentials(accessId, secretKey))
    autoScaling.setRegion(Region.getRegion(Regions.fromName(region)))
    autoScaling
  }
}

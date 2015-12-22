package com.netflix.spinnaker.clouddriver.aws.deploy.description

class AttachClassicLinkVpcDescription extends AbstractAmazonCredentialsDescription {
  String region
  String instanceId
  String vpcId
  List<String> securityGroupIds
}

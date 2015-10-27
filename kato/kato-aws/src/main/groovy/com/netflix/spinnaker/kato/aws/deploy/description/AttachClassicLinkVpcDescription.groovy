package com.netflix.spinnaker.kato.aws.deploy.description

class AttachClassicLinkVpcDescription extends AbstractAmazonCredentialsDescription {
  String region
  String instanceId
  String vpcId
  List<String> securityGroupIds
}

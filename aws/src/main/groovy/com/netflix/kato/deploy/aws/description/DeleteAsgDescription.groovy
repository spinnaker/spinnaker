package com.netflix.kato.deploy.aws.description

class DeleteAsgDescription extends AbstractAmazonCredentialsDescription {
  String asgName
  String region
  Boolean forceDelete = Boolean.FALSE
}

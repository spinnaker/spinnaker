package com.netflix.kato.deploy.aws.description

class UpsertAmazonDNSDescription extends AbstractAmazonCredentialsDescription {
  String type
  String name
  String target
  String hostedZoneName
}

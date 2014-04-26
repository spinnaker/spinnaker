package com.netflix.kato.security.aws

import groovy.transform.Immutable

@Immutable
class AmazonCredentials {
  String accessId
  String secretKey
  String environment
}

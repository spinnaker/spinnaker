package com.netflix.kato.security.aws

import com.amazonaws.auth.AWSCredentials
import groovy.transform.Canonical

@Canonical
class AmazonCredentials {
  final AWSCredentials credentials
  final String environment
}

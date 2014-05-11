package com.netflix.kato.security.gce

import com.google.api.services.compute.Compute
import groovy.transform.Canonical

@Canonical
class GoogleCredentials {
  final String project
  final Compute compute
}

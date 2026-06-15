/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.google.api.services.compute.model.TargetHttpsProxy
import spock.lang.Specification

class GoogleLoadBalancerCertificateExtractionSpec extends Specification {

  void "http load balancer cert helper handles null and empty ssl certificates"() {
    expect:
      GoogleHttpLoadBalancerCachingAgent.getFirstSslCertificateName(null) == null
      GoogleHttpLoadBalancerCachingAgent.getFirstSslCertificateName(new TargetHttpsProxy(sslCertificates: [])) == null
      GoogleHttpLoadBalancerCachingAgent.getFirstSslCertificateName(
        new TargetHttpsProxy(
          sslCertificates: ["https://www.googleapis.com/compute/v1/projects/test/global/sslCertificates/cert-a"]
        )
      ) == "cert-a"
  }

  void "internal http load balancer cert helper handles null and empty ssl certificates"() {
    given:
      def nullListProxy = new TargetHttpsProxy()
      def emptyListProxy = new TargetHttpsProxy(sslCertificates: [])
      def certProxy = new TargetHttpsProxy(
        sslCertificates: ["https://www.googleapis.com/compute/v1/projects/test/regions/us-central1/sslCertificates/cert-b"]
      )

    expect:
      GoogleInternalHttpLoadBalancerCachingAgent.getFirstSslCertificateName(nullListProxy) == null
      GoogleInternalHttpLoadBalancerCachingAgent.getFirstSslCertificateName(emptyListProxy) == null
      GoogleInternalHttpLoadBalancerCachingAgent.getFirstSslCertificateName(certProxy) == "cert-b"
  }

  void "ssl load balancer cert helper handles null and empty ssl certificates"() {
    expect:
      GoogleSslLoadBalancerCachingAgent.getFirstSslCertificate(null) == null
      GoogleSslLoadBalancerCachingAgent.getFirstSslCertificate([]) == null
      GoogleSslLoadBalancerCachingAgent.getFirstSslCertificate(
        ["https://www.googleapis.com/compute/v1/projects/test/global/sslCertificates/cert-c"]
      ) == "https://www.googleapis.com/compute/v1/projects/test/global/sslCertificates/cert-c"
  }

  // Compound scenario: a proxy using Certificate Manager has certificateMap set
  // but sslCertificates is null (GCP omits the field when a map takes precedence).
  // This is the exact production state the null-safety hardening addresses.
  void "http load balancer cert helper returns null when proxy uses certificateMap with no sslCertificates"() {
    given:
      def proxy = new TargetHttpsProxy(
        certificateMap: "//certificatemanager.googleapis.com/projects/test/locations/global/certificateMaps/my-map"
      )

    expect:
      proxy.getSslCertificates() == null
      GoogleHttpLoadBalancerCachingAgent.getFirstSslCertificateName(proxy) == null
      proxy.getCertificateMap() == "//certificatemanager.googleapis.com/projects/test/locations/global/certificateMaps/my-map"
  }
}

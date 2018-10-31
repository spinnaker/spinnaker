/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.model;

import com.oracle.bmc.loadbalancer.model.Backend;
import com.oracle.bmc.loadbalancer.model.BackendDetails;
import com.oracle.bmc.loadbalancer.model.SSLConfiguration;
import com.oracle.bmc.loadbalancer.model.HealthChecker;
import com.oracle.bmc.loadbalancer.model.HealthCheckerDetails;
import com.oracle.bmc.loadbalancer.model.SSLConfigurationDetails;

/**
 * Converts model to modelDetails.
 */
class Details {

  static BackendDetails of(Backend backend) {
    return BackendDetails.builder()
      .backup(backend.getBackup())
      .drain(backend.getDrain())
      .ipAddress(backend.getIpAddress())
      .offline(backend.getOffline())
      .port(backend.getPort())
      .weight(backend.getWeight()).build();
  }
  
  static HealthCheckerDetails of(HealthChecker healthChecker) {
    return HealthCheckerDetails.builder()
      .intervalInMillis(healthChecker.getIntervalInMillis())
      .port(healthChecker.getPort())
      .protocol(healthChecker.getProtocol())
      .responseBodyRegex(healthChecker.getResponseBodyRegex())
      .retries(healthChecker.getRetries())
      .returnCode(healthChecker.getReturnCode())
      .timeoutInMillis(healthChecker.getTimeoutInMillis())
      .urlPath(healthChecker.getUrlPath()).build();
  }
  
  static SSLConfigurationDetails of(SSLConfiguration sslConfig) {
    return SSLConfigurationDetails.builder()
      .certificateName(sslConfig.getCertificateName())
      .verifyDepth(sslConfig.getVerifyDepth())
      .verifyPeerCertificate(sslConfig.getVerifyPeerCertificate()).build();
  }
}

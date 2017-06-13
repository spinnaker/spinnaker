/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description

import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable

class CreateLoadBalancerDescription extends AbstractOracleBMCSCredentialsDescription implements ApplicationNameable {

  String application
  String stack
  String shape
  String policy
  List<String> subnetIds
  Listener listener
  HealthCheck healthCheck

  static class Listener {

    Integer port
    String protocol
  }

  static class HealthCheck {

    String protocol
    Integer port
    Integer interval
    Integer retries
    Integer timeout
    String url
    Integer statusCode
    String responseBodyRegex
  }
}

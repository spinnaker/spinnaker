/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.config

import groovy.transform.ToString

@ToString(includeNames = true)
class OracleConfigurationProperties {

  @ToString(includeNames = true)
  static class ManagedAccount {

    String name
    String environment
    String accountType
    List<String> requiredGroupMembership = []
    String compartmentId
    String userId
    String fingerprint
    String sshPrivateKeyFilePath
    String privateKeyPassphrase
    String tenancyId
    String region
  }

  List<ManagedAccount> accounts = []
}

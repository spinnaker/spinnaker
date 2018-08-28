/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.description

import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.ToString

@ToString(includeNames = true)
class ResizeOracleServerGroupDescription extends AbstractOracleCredentialsDescription {

  String accountName
  String region
  String serverGroupName
  ServerGroup.Capacity capacity = new ServerGroup.Capacity()
  //targetSize takes precedence if targetSize and capacity.desired are both specified.
  Integer targetSize
}

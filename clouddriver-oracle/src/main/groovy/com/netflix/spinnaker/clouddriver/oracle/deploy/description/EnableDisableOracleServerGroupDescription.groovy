/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.description

import com.netflix.spinnaker.clouddriver.deploy.description.EnableDisableDescriptionTrait
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupNameable

/**
 * "Enabling" means adding a server group to the target pool of each of its network load balancers.
 *
 * "Disabling" means removing a server group from the target pool of each of its network load balancers.
 */
class EnableDisableOracleServerGroupDescription extends AbstractOracleCredentialsDescription implements ServerGroupNameable, EnableDisableDescriptionTrait {

  String region
  String accountName

  @Override
  Collection<String> getServerGroupNames() {
    return [getServerGroupName()]
  }
}

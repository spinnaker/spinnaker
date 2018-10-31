/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.description;

import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;

import java.util.Collection;
import java.util.Collections;

public class DeleteLoadBalancerDescription extends AbstractOracleCredentialsDescription implements ApplicationNameable {
  String application;
  String loadBalancerId;
  
  public String getApplication() {
    return application;
  }
  public void setApplication(String application) {
    this.application = application;
  }
  public String getLoadBalancerId() {
    return loadBalancerId;
  }
  public void setLoadBalancerId(String loadBalancerId) {
    this.loadBalancerId = loadBalancerId;
  }

  @Override
  public Collection<String> getApplications() {
    return Collections.singleton(application);
  }
}

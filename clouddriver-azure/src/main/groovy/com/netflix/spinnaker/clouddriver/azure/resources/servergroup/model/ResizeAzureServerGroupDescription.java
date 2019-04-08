/*
 * Copyright 2019 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model;

import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class ResizeAzureServerGroupDescription extends AzureServerGroupDescription implements ServerGroupsNameable {
  private String serverGroupName;
  private Integer targetSize;
  private Capacity capacity;

  public String getServerGroupName() {
    return serverGroupName;
  }

  public void setServerGroupName(String serverGroupName) {
    this.serverGroupName = serverGroupName;
  }

  public Integer getTargetSize() {
    return targetSize;
  }

  public void setTargetSize(Integer targetSize) {
    this.targetSize = targetSize;
  }

  public Capacity getCapacity() {
    return capacity;
  }

  public void setCapacity(Capacity capacity) {
    this.capacity = capacity;
  }

  @Override
  public Collection<String> getServerGroupNames() {
    return new ArrayList<String>(Arrays.asList(serverGroupName));
  }
}

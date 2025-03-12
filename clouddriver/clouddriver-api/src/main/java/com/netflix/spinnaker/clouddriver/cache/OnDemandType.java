/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spinnaker.kork.annotations.Beta;
import java.util.Objects;
import lombok.Data;

@Beta
@Data
public class OnDemandType {
  private final String value;

  public static OnDemandType fromString(String value) {
    return new OnDemandType(value);
  }

  public static final OnDemandType ServerGroup = new OnDemandType("ServerGroup");
  public static final OnDemandType SecurityGroup = new OnDemandType("SecurityGroup");
  public static final OnDemandType LoadBalancer = new OnDemandType("LoadBalancer");
  public static final OnDemandType Job = new OnDemandType("Job");
  public static final OnDemandType TargetGroup = new OnDemandType("TargetGroup");
  public static final OnDemandType CloudFormation = new OnDemandType("CloudFormation");
  public static final OnDemandType Manifest = new OnDemandType("Manifest");
  public static final OnDemandType Function = new OnDemandType("Function");

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OnDemandType that = (OnDemandType) o;
    return Objects.equals(value.toLowerCase(), that.value.toLowerCase());
  }

  @Override
  public int hashCode() {
    return Objects.hash(value.toLowerCase());
  }

  @Override
  public String toString() {
    return value;
  }
}

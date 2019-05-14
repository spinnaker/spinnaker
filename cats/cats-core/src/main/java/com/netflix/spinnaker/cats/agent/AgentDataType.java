/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.cats.agent;

/**
 * Describes both the type name as well as authority for an Agent's provided data.
 *
 * <p>If an agent is an Authoritative source of data, then it's resulting data set will be
 * considered the current complete set for that data source. If an agent is an Informative source of
 * data, its results will contribute to the data set for that type, but is never considered the
 * complete set of data, so will not result in deletions when elements are no longer present.
 */
public class AgentDataType {
  public static enum Authority {
    AUTHORITATIVE,
    INFORMATIVE;

    public AgentDataType forType(String typeName) {
      return new AgentDataType(typeName, this);
    }
  }

  private final String typeName;
  private final Authority authority;

  public AgentDataType(String typeName, Authority authority) {
    this.typeName = typeName;
    this.authority = authority;
  }

  public String getTypeName() {
    return typeName;
  }

  public Authority getAuthority() {
    return authority;
  }
}

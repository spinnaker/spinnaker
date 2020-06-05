/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Collection;
import java.util.Collections;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractCloudFoundryServerGroupDescription
    extends AbstractCloudFoundryDescription implements ApplicationNameable {
  private String serverGroupId;
  private String serverGroupName;

  private String cluster;
  private Moniker moniker;

  @Override
  public Collection<String> getApplications() {
    if (moniker != null) {
      return Collections.singletonList(moniker.getApp());
    } else if (cluster != null) {
      return Collections.singletonList(Names.parseName(cluster).getApp());
    }
    return Collections.singletonList(Names.parseName(serverGroupName).getApp());
  }
}

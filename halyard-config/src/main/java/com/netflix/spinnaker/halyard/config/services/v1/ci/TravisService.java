/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.halyard.config.services.v1.ci;

import com.netflix.spinnaker.halyard.config.model.v1.ci.travis.TravisCi;
import com.netflix.spinnaker.halyard.config.model.v1.ci.travis.TravisMaster;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TravisService extends CiService<TravisMaster, TravisCi> {
  public TravisService(CiService.Members members) {
    super(members);
  }

  public String ciName() {
    return "travis";
  }

  protected List<TravisCi> getMatchingCiNodes(NodeFilter filter) {
    return lookupService.getMatchingNodesOfType(filter, TravisCi.class);
  }

  protected List<TravisMaster> getMatchingAccountNodes(NodeFilter filter) {
    return lookupService.getMatchingNodesOfType(filter, TravisMaster.class);
  }

  @Override
  public TravisMaster convertToAccount(Object object) {
    return objectMapper.convertValue(object, TravisMaster.class);
  }
}

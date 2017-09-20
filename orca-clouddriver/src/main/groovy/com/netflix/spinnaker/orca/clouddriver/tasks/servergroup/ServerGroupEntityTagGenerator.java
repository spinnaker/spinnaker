/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.orca.pipeline.model.Stage;

import java.util.Collection;
import java.util.Map;

public interface ServerGroupEntityTagGenerator {

  /**
   * Generates a collection of entity tags (e.g. server group provenance metadata) to be applied to a server group after deployment
   * @param stage the stage that performed the deployment
   * @return a collection of maps representing tags to send to Clouddriver
   */
  Collection<Map<String, Object>> generateTags(Stage stage, String serverGroup, String account, String location, String cloudProvider);
}

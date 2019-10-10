/*
 * Copyright 2019 New Relic Corporation. All rights reserved.
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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.newrelic;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.metricStores.AbstractEditMetricStoreCommand;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.newrelic.NewrelicStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;
import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "=")
public class EditNewrelicCommand extends AbstractEditMetricStoreCommand<NewrelicStore> {
  public MetricStores.MetricStoreType getMetricStoreType() {
    return MetricStores.MetricStoreType.NEWRELIC;
  }

  @Parameter(names = "--insert-key", description = "Your New Relic Insights insert key")
  private String insertKey;

  @Parameter(
      names = "--host",
      description =
          "The URL to post metric data to. In almost all cases, this is set correctly by default and should not be used.")
  private String host;

  @Parameter(
      names = "--tags",
      variableArity = true,
      description =
          "Your custom tags. Please delimit the KVP with colons i.e. --tags app:test env:dev")
  private List<String> tags = new ArrayList<>();

  @Parameter(
      names = "--add-tag",
      description =
          "Add this tag to the list of tags. Use the format key:value i.e. --add-tag app:test")
  private String addTag;

  @Parameter(
      names = "--remove-tag",
      description =
          "Remove this tag from the list of tags. Use the name of the tag you want to remove i.e. --remove-tag app")
  private String removeTag;

  @Override
  protected MetricStore editMetricStore(NewrelicStore newrelicStore) {
    newrelicStore.setInsertKey(isSet(insertKey) ? insertKey : newrelicStore.getInsertKey());
    newrelicStore.setHost(isSet(host) ? host : newrelicStore.getHost());

    try {
      newrelicStore.setTags(updateStringList(newrelicStore.getTags(), tags, addTag, removeTag));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --tags or --[add/remove]-tag");
    }

    return newrelicStore;
  }
}

/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.clouddriver.KatoRestService;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JobUtils implements CloudProviderAware {
  final RetrySupport retrySupport;
  final KatoRestService katoRestService;

  @Autowired
  public JobUtils(RetrySupport retrySupport, KatoRestService katoRestService) {
    this.retrySupport = retrySupport;
    this.katoRestService = katoRestService;
  }

  public void cancelWait(Stage stage) {
    Map<String, List<String>> jobs = (Map<String, List<String>>) stage.getContext().getOrDefault("deploy.jobs", new HashMap<>());
    String account = getCredentials(stage);

    for (Map.Entry<String, List<String>> entry : jobs.entrySet()) {
      String location = entry.getKey();
      List<String> names = entry.getValue();
      if (names == null || names.isEmpty()) {
        continue;
      }

      if (names.size() > 1) {
        throw new IllegalStateException("At most one job can be run and monitored at a time.");
      }

      String name = names.get(0);
      Names parsedName = Names.parseName(name);
      Moniker moniker = (Moniker) stage.getContext().get("moniker");
      String appName;

      if (moniker != null) {
        appName = moniker.getApp();
      } else {
        appName = (String) stage.getContext().getOrDefault("application", parsedName.getApp());
      }

      retrySupport.retry(() -> katoRestService.cancelJob(appName, account, location, name), 6, 5000, false);
    }
  }
}

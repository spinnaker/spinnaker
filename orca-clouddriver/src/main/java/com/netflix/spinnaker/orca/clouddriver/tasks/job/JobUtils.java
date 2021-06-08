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
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoRestService;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.front50.Front50Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Component
public class JobUtils implements CloudProviderAware {
  final RetrySupport retrySupport;
  final KatoRestService katoRestService;
  final Front50Service front50Service;

  @Autowired
  public JobUtils(
      RetrySupport retrySupport,
      KatoRestService katoRestService,
      Optional<Front50Service> front50Service) {
    this.retrySupport = retrySupport;
    this.katoRestService = katoRestService;
    this.front50Service = front50Service.orElse(null);
  }

  public void cancelWait(StageExecution stage) {
    Map<String, List<String>> jobs =
        (Map<String, List<String>>) stage.getContext().getOrDefault("deploy.jobs", new HashMap<>());
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
      String appName, validAppName;

      if (moniker != null) {
        appName = moniker.getApp();
      } else {
        appName =
            (String)
                stage
                    .getContext()
                    .getOrDefault("application", stage.getExecution().getApplication());
      }

      if (appName == null && applicationExists(parsedName.getApp())) {
        appName = parsedName.getApp();
      }

      validAppName = appName;
      retrySupport.retry(
          () -> katoRestService.cancelJob(validAppName, account, location, name), 6, 5000, false);
    }
  }

  private Boolean applicationExists(String appName) {
    if (appName == null || front50Service == null) {
      return false;
    }
    try {
      return front50Service.get(appName) != null;
    } catch (RetrofitError e) {
      throw e;
    }
  }
}

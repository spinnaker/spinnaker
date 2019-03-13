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

package com.netflix.spinnaker.echo.build;

import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.echo.services.IgorService;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty("igor.enabled")
@RequiredArgsConstructor
/*
 * Given a build event, fetches information about that build from Igor
 */
public class BuildInfoService {
  private final IgorService igorService;
  private final RetrySupport retrySupport;

  public Map<String, Object> getBuildInfo(BuildEvent event) {
    String master = event.getContent().getMaster();
    String job = event.getContent().getProject().getName();
    int buildNumber = event.getBuildNumber();

    if (StringUtils.isNoneEmpty(master, job)) {
      return retry(() -> igorService.getBuild(buildNumber, master, job));
    }
    return Collections.emptyMap();
  }

  public Map<String, Object> getProperties(BuildEvent event, String propertyFile) {
    String master = event.getContent().getMaster();
    String job = event.getContent().getProject().getName();
    int buildNumber = event.getBuildNumber();

    if (StringUtils.isNoneEmpty(master, job, propertyFile)) {
      return retry(() -> igorService.getPropertyFile(buildNumber, propertyFile, master, job));
    }
    return Collections.emptyMap();
  }

  public List<Artifact> getArtifacts(BuildEvent event, String propertyFile) {
    String master = event.getContent().getMaster();
    String job = event.getContent().getProject().getName();
    int buildNumber = event.getBuildNumber();
    if (StringUtils.isNoneEmpty(master, job, propertyFile)) {
      return retry(() -> igorService.getArtifacts(buildNumber, propertyFile, master, job));
    }
    return Collections.emptyList();
  }

  private <T> T retry(Supplier<T> supplier) {
    return retrySupport.retry(supplier, 5, 1000, true);
  }
}

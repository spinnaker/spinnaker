/*
 * Copyright 2019 Netflix, Inc.
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
 */
package com.netflix.spinnaker.clouddriver.titus.deploy.actions;

import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.PrepareTitusDeploy.PrepareTitusDeployCommand;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class TitusServiceJobPredicate implements Predicate<Saga> {
  @Override
  public boolean test(Saga saga) {
    return saga.getEvents().stream()
        .filter(e -> PrepareTitusDeployCommand.class.isAssignableFrom(e.getClass()))
        .findFirst()
        .map(
            e -> {
              String jobType = ((PrepareTitusDeployCommand) e).getDescription().getJobType();
              return JobType.isEqual(jobType, JobType.SERVICE);
            })
        .orElseThrow(
            () ->
                new TitusException(
                    "Could not determine job type: No TitusDeployDescription found"));
  }
}

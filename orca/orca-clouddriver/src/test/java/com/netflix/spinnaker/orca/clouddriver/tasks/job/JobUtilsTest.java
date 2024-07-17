/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.clouddriver.KatoRestService;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JobUtilsTest {

  private final String ACCOUNT = "test-account";

  private final String APPLICATION = "test-app";

  private final RetrySupport retrySupport = new RetrySupport();

  private KatoRestService katoRestService = mock(KatoRestService.class);

  private Front50Service front50Service = mock(Front50Service.class);

  private JobUtils jobUtils =
      new JobUtils(retrySupport, katoRestService, Optional.of(front50Service));

  @Test
  void testCancelWaitWithJobAndMoniker() {
    Map<String, Object> context = new HashMap<>();
    context.put("account", ACCOUNT);
    context.put("deploy.jobs", Map.of("default", List.of("job my-job")));
    Map<String, Object> moniker = new HashMap<>();
    moniker.put("app", APPLICATION);
    context.put("moniker", moniker);
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(ExecutionType.PIPELINE, APPLICATION), "test", context);

    assertThatCode(() -> jobUtils.cancelWait(stage)).doesNotThrowAnyException();

    verify(katoRestService).cancelJob(APPLICATION, ACCOUNT, "default", "job my-job");
  }
}

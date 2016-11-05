/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.entitytags;

import com.netflix.spinnaker.orca.DefaultTaskResult;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.spinnaker.orca.clouddriver.OortService.EntityTags;
import retrofit.RetrofitError;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DeleteEntityTagsTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  private final KatoService kato;
  private final OortService oort;

  @Autowired
  public DeleteEntityTagsTask(KatoService kato, OortService oort) {
    this.kato = kato;
    this.oort = oort;
  }

  @Override
  public TaskResult execute(Stage stage) {
    EntityTags entityTags = null;

    try {
      entityTags = oort.getEntityTags((String) stage.getContext().get("id"));
    } catch (RetrofitError e) {
      if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
        // tag doesn't exist, nothing to delete
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED);
      }
    }

    Map operationContext = new HashMap(stage.getContext());
    operationContext.put("account", entityTags.getEntityRef().attributes().get("account"));

    TaskId taskId = kato.requestOperations(Collections.singletonList(
      new HashMap<String, Map>() {{
        put("deleteEntityTags", operationContext);
      }})
    ).toBlocking().first();

    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, new HashMap<String, Object>() {{
      put("notification.type", "deleteentitytags");
      put("kato.last.task.id", taskId);
    }});
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(1);
  }
}

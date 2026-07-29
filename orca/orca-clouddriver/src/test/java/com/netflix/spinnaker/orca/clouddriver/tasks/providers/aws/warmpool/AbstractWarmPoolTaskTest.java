/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.warmpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AbstractWarmPoolTaskTest {

  private TargetServerGroup tsg(String name, String region) {
    return new TargetServerGroup(Map.of("name", name, "region", region, "asg", Map.of()));
  }

  @Test
  void shouldResolveTargetServerGroupAndSubmitOpsRequest() {
    KatoService katoService = mock(KatoService.class);
    when(katoService.requestOperations(any(), any())).thenReturn(new TaskId("1"));

    TargetServerGroupResolver resolver = mock(TargetServerGroupResolver.class);
    when(resolver.resolve(any())).thenReturn(Collections.singletonList(tsg("targetAsg", "us-west-1")));

    Map<String, Object> context = new HashMap<>();
    context.put("region", "us-west-1");
    context.put("minSize", 2);
    context.put("poolState", "Stopped");
    StageExecutionImpl stage =
        new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), null, null, context);

    UpsertWarmPoolTask task = new UpsertWarmPoolTask();
    task.katoService = katoService;
    task.resolver = resolver;

    TaskResult result = task.execute(stage);

    assertThat(result.getContext().get("asgName")).isEqualTo("targetAsg");
    assertThat(((TaskId) result.getContext().get("kato.last.task.id")).getId()).isEqualTo("1");
    assertThat(result.getContext().get("deploy.server.groups"))
        .isEqualTo(Map.of("us-west-1", List.of("targetAsg")));
  }

  @Test
  void shouldSendResolvedAsgToKatoInAsgsField() {
    KatoService katoService = mock(KatoService.class);
    when(katoService.requestOperations(any(), any())).thenReturn(new TaskId("1"));

    TargetServerGroupResolver resolver = mock(TargetServerGroupResolver.class);
    when(resolver.resolve(any())).thenReturn(Collections.singletonList(tsg("targetAsg", "us-west-1")));

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("region", "us-west-1");
    ctx.put("cloudProvider", "abc");
    ctx.put("minSize", 2);
    ctx.put("poolState", "Stopped");
    StageExecutionImpl stage =
        new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), null, null, ctx);

    UpsertWarmPoolTask task = new UpsertWarmPoolTask();
    task.katoService = katoService;
    task.resolver = resolver;

    task.execute(stage);

    verify(katoService)
        .requestOperations(
            eq("abc"),
            argThat(
                ops -> {
                  Map<String, Object> desc =
                      (Map<String, Object>) ((Map) ops.iterator().next()).get("upsertWarmPoolDescription");
                  List<Map<String, String>> asgs = (List<Map<String, String>>) desc.get("asgs");
                  return asgs.get(0).get("serverGroupName").equals("targetAsg")
                      && asgs.get(0).get("region").equals("us-west-1")
                      && Integer.valueOf(2).equals(desc.get("minSize"))
                      && "Stopped".equals(desc.get("poolState"));
                }));
  }
}

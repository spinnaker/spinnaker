/*
 *
 *  * Copyright 2019 Google, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License")
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import rx.Observable;

@RunWith(JUnit4.class)
public class SetStatefulDiskTaskTest {

  private SetStatefulDiskTask task;

  private KatoService katoService;
  private TargetServerGroupResolver resolver;

  @Before
  public void setUp() {
    katoService = mock(KatoService.class);
    resolver = mock(TargetServerGroupResolver.class);
    task = new SetStatefulDiskTask(katoService, resolver);
  }

  @Test
  public void success() {
    when(resolver.resolve(any()))
        .thenReturn(
            ImmutableList.of(new TargetServerGroup(ImmutableMap.of("name", "testapp-v000"))));
    when(katoService.requestOperations(any(), any()))
        .thenReturn(Observable.just(new TaskId("10111")));

    Stage stage = new Stage();
    stage.getContext().put("cloudProvider", "gce");
    stage.getContext().put("credentials", "spinnaker-test");
    stage.getContext().put("serverGroupName", "testapp-v000");
    stage.getContext().put("region", "us-desertoasis1");
    stage.getContext().put("deviceName", "testapp-v000-1");

    TaskResult result = task.execute(stage);

    ImmutableMap<String, String> operationParams =
        ImmutableMap.of(
            "credentials", "spinnaker-test",
            "serverGroupName", "testapp-v000",
            "region", "us-desertoasis1",
            "deviceName", "testapp-v000-1");
    verify(katoService)
        .requestOperations(
            "gce", ImmutableList.of(ImmutableMap.of("setStatefulDisk", operationParams)));

    assertThat(result.getContext().get("notification.type")).isEqualTo("setstatefuldisk");
    assertThat(result.getContext().get("serverGroupName")).isEqualTo("testapp-v000");
    assertThat(result.getContext().get("deploy.server.groups"))
        .isEqualTo(ImmutableMap.of("us-desertoasis1", ImmutableList.of("testapp-v000")));
  }
}

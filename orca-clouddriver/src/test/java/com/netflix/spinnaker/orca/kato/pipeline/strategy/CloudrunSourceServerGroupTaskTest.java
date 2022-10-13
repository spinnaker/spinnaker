/*
 * Copyright 2022 OpsMx, Inc
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
 *
 */

package com.netflix.spinnaker.orca.kato.pipeline.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class CloudrunSourceServerGroupTaskTest {

  private CloudrunSourceServerGroupTask task;

  @Mock private SourceResolver sourceResolver;

  private ObjectMapper mapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @BeforeEach
  void setUp() {
    task = new CloudrunSourceServerGroupTask(sourceResolver);
  }

  @Test
  void shouldParseRegionAndIncludeInContext() throws IOException {
    String stageJson =
        "{\"application\":\"democloudrun\",\"configFiles\":[\"apiVersion: serving.knative.dev/v1\\nkind: Service\\nmetadata:\\n  "
            + "name: democloudrun--dtl\\n  namespace: '135005621049'\\n  selfLink: /apis/serving.knative.dev/v1/namespaces/135005621049/services/democloudrun--dtl\\n  "
            + "uid: 35e588c7-04f4-4e64-a4e3-9eaff2134a3b\\n  resourceVersion: AAXmW62mFwY\\n  generation: 7\\n  creationTimestamp: "
            + "'2022-08-16T11:42:56.437832Z'\\n  labels:\\n    cloud.googleapis.com/location: us-central1\\n  annotations:\\n    "
            + "run.googleapis.com/client-name: cloud-console\\n    serving.knative.dev/creator: "
            + "spinnaker-cloudrun-account@my-project-71824.iam.gserviceaccount.com\\n    "
            + "serving.knative.dev/lastModifier: spinnaker-cloudrun-account@my-project-71824.iam.gserviceaccount.com\\n    "
            + "client.knative.dev/user-image: us-docker.pkg.dev/cloudrun/container/hello\\n    run.googleapis.com/ingress: all\\n    "
            + "run.googleapis.com/ingress-status: all\\nspec:\\n  template:\\n    metadata:\\n      name: democloudrun--dtl-v004\\n      "
            + "annotations:\\n        run.googleapis.com/client-name: cloud-console\\n        autoscaling.knative.dev/minScale: '1'\\n        "
            + "autoscaling.knative.dev/maxScale: '3'\\n    spec:\\n      containerConcurrency: 80\\n      timeoutSeconds: 200\\n      "
            + "serviceAccountName: spinnaker-cloudrun-account@my-project-71824.iam.gserviceaccount.com\\n      containers:\\n      "
            + "- image: us-docker.pkg.dev/cloudrun/container/hello\\n        ports:\\n        - name: http1\\n          containerPort: 8080\\n        "
            + "resources:\\n          limits:\\n            cpu: 1000m\\n            "
            + "memory: 256Mi\"],\"cloudProvider\":\"cloudrun\",\"provider\":\"cloudrun\",\"credentials\":\"my-cloudrun-account\","
            + "\"gitCredentialType\":\"NONE\",\"sourceType\":\"git\",\"configArtifacts\":[],\"interestingHealthProviderNames\":[],"
            + "\"fromArtifact\":false,\"account\":\"my-cloudrun-account\",\"freeFormDetails\":\"dtl\",\"user\":\"[anonymous]\"}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    StageData result = task.parseRegionAndSetInContext(stage);
    assertThat(result.getRegion()).isEqualTo("us-central1");
  }

  @Test
  void noRegionInJsonAndShouldNotIncludeInContext() throws IOException {

    String stageJson =
        "{\"application\":\"democloudrun\",\"configFiles\":[\"apiVersion: serving.knative.dev/v1\\nkind: Service\\nmetadata:\\n  "
            + "name: democloudrun--dtl\\n  namespace: '135005621049'\\n  selfLink: /apis/serving.knative.dev/v1/namespaces/135005621049/services/democloudrun--dtl\\n  "
            + "uid: 35e588c7-04f4-4e64-a4e3-9eaff2134a3b\\n  resourceVersion: AAXmW62mFwY\\n  generation: 7\\n  creationTimestamp: "
            + "'2022-08-16T11:42:56.437832Z'\\n  labels:\\n    cloud.googleapis.com/location: \\n  annotations:\\n    "
            + "run.googleapis.com/client-name: cloud-console\\n    serving.knative.dev/creator: "
            + "spinnaker-cloudrun-account@my-project-71824.iam.gserviceaccount.com\\n    "
            + "serving.knative.dev/lastModifier: spinnaker-cloudrun-account@my-project-71824.iam.gserviceaccount.com\\n    "
            + "client.knative.dev/user-image: us-docker.pkg.dev/cloudrun/container/hello\\n    run.googleapis.com/ingress: all\\n    "
            + "run.googleapis.com/ingress-status: all\\nspec:\\n  template:\\n    metadata:\\n      name: democloudrun--dtl-v004\\n      "
            + "annotations:\\n        run.googleapis.com/client-name: cloud-console\\n        autoscaling.knative.dev/minScale: '1'\\n        "
            + "autoscaling.knative.dev/maxScale: '3'\\n    spec:\\n      containerConcurrency: 80\\n      timeoutSeconds: 200\\n      "
            + "serviceAccountName: spinnaker-cloudrun-account@my-project-71824.iam.gserviceaccount.com\\n      containers:\\n      "
            + "- image: us-docker.pkg.dev/cloudrun/container/hello\\n        ports:\\n        - name: http1\\n          containerPort: 8080\\n        "
            + "resources:\\n          limits:\\n            cpu: 1000m\\n            "
            + "memory: 256Mi\"],\"cloudProvider\":\"cloudrun\",\"provider\":\"cloudrun\",\"credentials\":\"my-cloudrun-account\","
            + "\"gitCredentialType\":\"NONE\",\"sourceType\":\"git\",\"configArtifacts\":[],\"interestingHealthProviderNames\":[],"
            + "\"fromArtifact\":false,\"account\":\"my-cloudrun-account\",\"freeFormDetails\":\"dtl\",\"user\":\"[anonymous]\"}";

    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    assertThatThrownBy(() -> task.execute(stage)).isInstanceOf(IllegalStateException.class);
  }
}

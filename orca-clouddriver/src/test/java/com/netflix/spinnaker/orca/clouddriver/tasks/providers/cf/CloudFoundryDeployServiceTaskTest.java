/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestEvaluator;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;

class CloudFoundryDeployServiceTaskTest {
  @Test
  void bindArtifacts() throws IOException {
    String stageJson =
        "{\n"
            + "  \"cloudProvider\": \"cloudfoundry\",\n"
            + "  \"credentials\": \"montclair\",\n"
            + "  \"manifest\": {\n"
            + "    \"artifact\": {\n"
            + "      \"artifactAccount\": \"spring-artifactory\",\n"
            + "      \"reference\": \"g:a:${expression}\",\n"
            + "      \"type\": \"maven/file\"\n"
            + "    }\n"
            + "  },\n"
            + "  \"name\": \"Deploy Service\",\n"
            + "  \"region\": \"development > development\",\n"
            + "  \"type\": \"deployService\"\n"
            + "}";

    ObjectMapper mapper =
        new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    KatoService katoService = mock(KatoService.class);
    when(katoService.requestOperations(any(), any()))
        .thenReturn(Observable.just(new TaskId("taskid")));

    ManifestEvaluator manifestEvaluator = mock(ManifestEvaluator.class);
    ImmutableList<Map<Object, Object>> returnedManifests =
        ImmutableList.of(Collections.singletonMap("serviceNameInstance", "foo"));
    when(manifestEvaluator.evaluate(any(), any()))
        .thenReturn(new ManifestEvaluator.Result(returnedManifests, null, null));

    CloudFoundryDeployServiceTask task =
        new CloudFoundryDeployServiceTask(mapper, katoService, manifestEvaluator);
    Stage stage = new Stage();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    task.execute(stage);

    ArgumentCaptor<Collection<Map<String, Map>>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(katoService).requestOperations(eq("cloudfoundry"), captor.capture());

    Map<String, Map> operation = captor.getValue().iterator().next();
    List<Map> manifest = (List) operation.get("deployService").get("manifest");
    assertThat(manifest).isEqualTo(returnedManifests);
  }
}

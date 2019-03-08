package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CloudFoundryDeployServiceTaskTest {
  @Test
  void bindArtifacts() throws IOException {
    String stageJson = "{\n" +
      "  \"cloudProvider\": \"cloudfoundry\",\n" +
      "  \"credentials\": \"montclair\",\n" +
      "  \"manifest\": {\n" +
      "    \"artifact\": {\n" +
      "      \"artifactAccount\": \"spring-artifactory\",\n" +
      "      \"reference\": \"g:a:${expression}\",\n" +
      "      \"type\": \"maven/file\"\n" +
      "    }\n" +
      "  },\n" +
      "  \"name\": \"Deploy Service\",\n" +
      "  \"region\": \"development > development\",\n" +
      "  \"type\": \"deployService\"\n" +
      "}";

    ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    KatoService katoService = mock(KatoService.class);
    when(katoService.requestOperations(any(), any())).thenReturn(Observable.just(new TaskId("taskid")));

    ArtifactResolver artifactResolver = mock(ArtifactResolver.class);
    Artifact boundArtifact = Artifact.builder()
      .reference("g:a:v").type("maven/file").artifactAccount("spring-artifactory").build();
    when(artifactResolver.getBoundArtifactForStage(any(), isNull(), any())).thenReturn(boundArtifact);

    CloudFoundryDeployServiceTask task = new CloudFoundryDeployServiceTask(katoService, artifactResolver);
    Stage stage = new Stage();
    stage.setContext(mapper.readValue(stageJson, Map.class));
    task.execute(stage);

    ArgumentCaptor<Collection<Map<String, Map>>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(katoService).requestOperations(eq("cloudfoundry"), captor.capture());

    Map<String, Map> operation = captor.getValue().iterator().next();
    Map manifest = (Map) operation.get("deployService").get("manifest");
    Object capturedBoundArtifact = manifest.get("artifact");
    assertThat(boundArtifact).isEqualTo(mapper.convertValue(capturedBoundArtifact, Artifact.class));
  }
}

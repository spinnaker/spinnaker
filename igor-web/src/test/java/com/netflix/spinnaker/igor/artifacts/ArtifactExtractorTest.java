package com.netflix.spinnaker.igor.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.igor.Main;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Main.class)
@TestPropertySource(properties = {"spring.application.name = igor"})
public class ArtifactExtractorTest {

  @Autowired private ArtifactExtractor artifactExtractor;

  @Test
  public void should_be_able_to_serialize_jsr310_dates() {
    GenericBuild build = new GenericBuild();
    build.setProperties(
        Map.of(
            "group", "test.group",
            "artifact", "test-artifact",
            "version", "1.0",
            "messageFormat", "JAR",
            "customFormat", "false"));
    build.setGenericGitRevisions(
        List.of(
            GenericGitRevision.builder()
                .sha1("eab6604100035dbaae240550c38bef5043d928c6")
                .committer("My Name")
                .timestamp(Instant.parse("2022-06-22T14:55:03Z"))
                .build()));

    List<Artifact> artifacts = artifactExtractor.extractArtifacts(build);
    assertThat(artifacts).hasSize(1);
    Artifact artifact = artifacts.get(0);
    assertThat(artifact.getName()).isEqualTo("test-artifact-1.0");
    assertThat(artifact.getVersion()).isEqualTo("1.0");
  }
}

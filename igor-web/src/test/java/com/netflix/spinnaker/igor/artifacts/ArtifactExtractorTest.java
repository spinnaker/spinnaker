package com.netflix.spinnaker.igor.artifacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.spinnaker.igor.Main;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.parsing.DefaultJinjavaFactory;
import com.netflix.spinnaker.kork.artifacts.parsing.JinjaArtifactExtractor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {
      "spring.application.name = igor",
      "spring.mvc.pathmatch.matching-strategy = ANT_PATH_MATCHER"
    })
public class ArtifactExtractorTest {

  @Autowired private ArtifactExtractor artifactExtractor;

  @Autowired private JinjaTemplateService jinjaTemplateService;

  private JinjaArtifactExtractor.Factory jinjaArtifactExtractorFactory;
  private ObjectMapper objectMapper;
  private GenericBuild build;

  @BeforeEach
  public void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    jinjaArtifactExtractorFactory = new JinjaArtifactExtractor.Factory(new DefaultJinjavaFactory());
    artifactExtractor =
        new ArtifactExtractor(objectMapper, jinjaTemplateService, jinjaArtifactExtractorFactory);

    build = new GenericBuild();
    Map<String, Object> properties =
        Map.of(
            "group", "test.group",
            "artifact", "test-artifact",
            "version", "1.0",
            "messageFormat", "JAR",
            "customFormat", "false");
    build.setProperties(properties);
  }

  @Test
  public void should_be_able_to_serialize_jsr310_dates() {
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

  @Test
  public void testParsesAnArtifactReturnsItsArtifacts() {
    List<Artifact> artifacts = artifactExtractor.extractArtifacts(build);

    assertEquals(1, artifacts.size());
    assertEquals("test-artifact-1.0", artifacts.get(0).getName());
  }

  @ParameterizedTest
  @CsvSource({"true, true", "false, false", "'true', true", "'false', false"})
  public void testParseCustomFormatCorrectlyParsesBooleansAndStrings(
      Object customFormat, boolean expectedResult) {
    boolean result = artifactExtractor.parseCustomFormat(customFormat);
    assertThat(result).isEqualTo(expectedResult);
  }
}

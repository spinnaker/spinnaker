package com.netflix.spinnaker.kork.artifacts;

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDecorator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtifactTypeDecoratorTest {
  public static Stream<Arguments> toRemoteArgs() {
    return Stream.of(
        Arguments.of(
            Artifact.builder().type(ArtifactTypes.EMBEDDED_BASE64.getMimeType()).build(),
            ArtifactTypes.REMOTE_BASE64.getMimeType()),
        Arguments.of(
            Artifact.builder().type(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType()).build(),
            ArtifactTypes.REMOTE_MAP_BASE64.getMimeType()));
  }

  public Stream<Arguments> toEmbeddedArgs() {
    return Stream.of(
        Arguments.of(
            Artifact.builder().type(ArtifactTypes.REMOTE_BASE64.getMimeType()).build(),
            ArtifactTypes.EMBEDDED_BASE64.getMimeType()),
        Arguments.of(
            Artifact.builder().type(ArtifactTypes.REMOTE_MAP_BASE64.getMimeType()).build(),
            ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType()));
  }

  @ParameterizedTest
  @MethodSource("toRemoteArgs")
  public void toRemote(Artifact artifact, String expectedType) {
    ArtifactDecorator decorator = ArtifactTypeDecorator.toRemote(artifact);

    Artifact.ArtifactBuilder builder = Artifact.builder();
    builder = decorator.decorate(builder);
    Artifact result = builder.build();
    assertEquals(expectedType, result.getType());
  }

  @ParameterizedTest
  @MethodSource("toEmbeddedArgs")
  public void toEmbedded(Artifact artifact, String expectedType) {
    ArtifactDecorator decorator = ArtifactTypeDecorator.toEmbedded(artifact);

    Artifact.ArtifactBuilder builder = Artifact.builder();
    builder = decorator.decorate(builder);
    Artifact result = builder.build();
    assertEquals(expectedType, result.getType());
  }
}

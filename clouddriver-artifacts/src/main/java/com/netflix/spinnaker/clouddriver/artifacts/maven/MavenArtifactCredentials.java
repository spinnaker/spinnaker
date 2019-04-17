/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.maven;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.Getter;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.DefaultRepositoryLayoutProvider;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionScheme;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;

public class MavenArtifactCredentials implements ArtifactCredentials {
  private static final String RELEASE = "RELEASE";
  private static final String SNAPSHOT = "SNAPSHOT";
  private static final String LATEST = "LATEST";
  private static final String MAVEN_METADATA_XML = "maven-metadata.xml";

  private final MavenArtifactAccount account;
  private final OkHttpClient okHttpClient;
  private final RepositoryLayout repositoryLayout;

  @Getter
  private final List<String> types = singletonList("maven/file");

  public MavenArtifactCredentials(MavenArtifactAccount account, OkHttpClient okHttpClient) {
    this.account = account;
    this.okHttpClient = okHttpClient;

    try {
      RemoteRepository remoteRepository = new RemoteRepository.Builder(account.getName(), "default",
        account.getRepositoryUrl()).build();
      this.repositoryLayout = MavenRepositorySystemUtils.newServiceLocator()
        .addService(RepositoryLayoutProvider.class, DefaultRepositoryLayoutProvider.class)
        .getService(RepositoryLayoutProvider.class)
        .newRepositoryLayout(MavenRepositorySystemUtils.newSession(), remoteRepository);
    } catch (NoRepositoryLayoutException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String getName() {
    return account.getName();
  }

  @Override
  public InputStream download(Artifact artifact) {
    try {
      DefaultArtifact requestedArtifact = new DefaultArtifact(artifact.getReference());
      String artifactPath = resolveVersion(requestedArtifact)
        .map(version -> repositoryLayout.getLocation(withVersion(requestedArtifact, version), false))
        .map(URI::getPath)
        .orElseThrow(() -> new IllegalStateException("No versions matching constraint '" + artifact.getVersion() + "' for '" +
          artifact.getReference() + "'"));

      Request artifactRequest = new Request.Builder()
        .url(account.getRepositoryUrl() + "/" + artifactPath)
        .get()
        .build();

      Response artifactResponse = okHttpClient.newCall(artifactRequest).execute();
      if (artifactResponse.isSuccessful()) {
        return artifactResponse.body().byteStream();
      }
      throw new IllegalStateException("Unable to download artifact with reference '" + artifact.getReference() + "'. HTTP " +
        artifactResponse.code());
    } catch (IOException | ArtifactDownloadException e) {
      throw new IllegalStateException("Unable to download artifact with reference '" + artifact.getReference() + "'", e);
    }
  }

  private Optional<String> resolveVersion(org.eclipse.aether.artifact.Artifact artifact) {
    try {
      String metadataPath = metadataUri(artifact).getPath();
      Request metadataRequest = new Request.Builder()
        .url(account.getRepositoryUrl() + "/" + metadataPath)
        .get()
        .build();
      Response response = okHttpClient.newCall(metadataRequest).execute();

      if (response.isSuccessful()) {
        VersionScheme versionScheme = new GenericVersionScheme();
        VersionConstraint versionConstraint = versionScheme.parseVersionConstraint(artifact.getVersion());
        Versioning versioning = new MetadataXpp3Reader().read(response.body().byteStream(), false).getVersioning();

        if (isRelease(artifact)) {
          return Optional.ofNullable(versioning.getRelease());
        } else if (isLatestSnapshot(artifact)) {
          return resolveVersion(withVersion(artifact, versioning.getLatest()));
        } else if (isLatest(artifact)) {
          String latestVersion = versioning.getLatest();
          return latestVersion != null && latestVersion.endsWith("-SNAPSHOT") ?
            resolveVersion(withVersion(artifact, latestVersion)) : Optional.ofNullable(latestVersion);
        } else if (artifact.getVersion().endsWith("-SNAPSHOT")) {
          String requestedClassifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
          return versioning.getSnapshotVersions().stream()
            .filter(v -> v.getClassifier().equals(requestedClassifier))
            .map(SnapshotVersion::getVersion)
            .findFirst();
        } else {
          return versioning
            .getVersions()
            .stream()
            .map(v -> {
              try {
                return versionScheme.parseVersion(v);
              } catch (InvalidVersionSpecificationException e) {
                throw new ArtifactDownloadException(e);
              }
            })
            .filter(versionConstraint::containsVersion)
            .max(Version::compareTo)
            .map(Version::toString);
        }
      } else {
        throw new IOException("Unsuccessful response retrieving maven-metadata.xml " + response.code());
      }
    } catch (IOException | XmlPullParserException | InvalidVersionSpecificationException e) {
      throw new ArtifactDownloadException(e);
    }
  }

  private DefaultArtifact withVersion(org.eclipse.aether.artifact.Artifact artifact, String version) {
    return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
      artifact.getExtension(), version);
  }

  private URI metadataUri(org.eclipse.aether.artifact.Artifact artifact) {
    String group = artifact.getGroupId();
    String artifactId = artifact.getArtifactId();
    String version = artifact.getVersion();

    Metadata metadata;
    if (artifact.getVersion().endsWith("-SNAPSHOT")) {
      metadata = new DefaultMetadata(group, artifactId, version, MAVEN_METADATA_XML, Metadata.Nature.SNAPSHOT);
    } else if (isRelease(artifact)) {
      metadata = new DefaultMetadata(group, artifactId, MAVEN_METADATA_XML, Metadata.Nature.RELEASE);
    } else if (isLatestSnapshot(artifact)) {
      metadata = new DefaultMetadata(group, artifactId, MAVEN_METADATA_XML, Metadata.Nature.SNAPSHOT);
    } else if (isLatest(artifact) || version.startsWith("[") || version.startsWith("(")) {
      metadata = new DefaultMetadata(group, artifactId, MAVEN_METADATA_XML, Metadata.Nature.RELEASE_OR_SNAPSHOT);
    } else {
      metadata = new DefaultMetadata(group, artifactId, MAVEN_METADATA_XML, Metadata.Nature.RELEASE);
    }

    return repositoryLayout.getLocation(metadata, false);
  }

  private boolean isRelease(org.eclipse.aether.artifact.Artifact artifact) {
    return RELEASE.equals(artifact.getVersion()) || "latest.release".equals(artifact.getVersion());
  }

  private boolean isLatestSnapshot(org.eclipse.aether.artifact.Artifact artifact) {
    return SNAPSHOT.equals(artifact.getVersion()) || "latest.integration".equals(artifact.getVersion());
  }

  private boolean isLatest(org.eclipse.aether.artifact.Artifact artifact) {
    return LATEST.equals(artifact.getVersion());
  }

  private static class ArtifactDownloadException extends RuntimeException {
    ArtifactDownloadException(Throwable cause) {
      super(cause);
    }
  }
}

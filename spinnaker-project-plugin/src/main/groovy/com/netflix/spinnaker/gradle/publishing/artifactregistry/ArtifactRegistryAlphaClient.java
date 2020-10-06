package com.netflix.spinnaker.gradle.publishing.artifactregistry;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Key;
import com.google.api.services.artifactregistry.v1beta1.ArtifactRegistry;
import com.google.api.services.artifactregistry.v1beta1.ArtifactRegistryRequest;
import com.google.api.services.artifactregistry.v1beta1.model.Operation;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;

/**
 * The package import API is only in alpha, and Google doesn't publish a client library for the
 * Artifact Registry alpha APIs. So this extends the beta API client and adds support for that one
 * method.
 */
final class ArtifactRegistryAlphaClient extends ArtifactRegistry {

  public ArtifactRegistryAlphaClient(HttpTransport transport,
                                     JsonFactory jsonFactory,
                                     HttpRequestInitializer httpRequestInitializer) {
    super(transport, jsonFactory, httpRequestInitializer);
  }

  ImportArtifacts importArtifacts(String project, String location, String repository, String... gcsUris)
    throws IOException {
    ImportArtifacts result = new ImportArtifacts(this, project, location, repository, gcsUris);
    initialize(result);
    return result;
  }

  static final class ImportArtifacts extends ArtifactRegistryRequest<Operation> {

    private static final String REST_PATH = "v1alpha1/{+parent}:import";

    @Key
    private String parent;

    ImportArtifacts(ArtifactRegistryAlphaClient client,
                    String project,
                    String location,
                    String repository,
                    String... gcsUris) {
      super(
        client, "POST", REST_PATH, new ImportArtifactsRequest().setGcsSource(
          new GcsSource().setUris(ImmutableList.copyOf(gcsUris))
        ), Operation.class
      );
      this.parent = String.format("projects/%s/locations/%s/repositories/%s", project, location, repository);
    }

    public String getParent() {
      return parent;
    }

    public ImportArtifacts setParent(String parent) {
      this.parent = parent;
      return this;
    }
  }

  static final class ImportArtifactsRequest extends GenericJson {

    @Key
    private GcsSource gcsSource;

    public GcsSource getGcsSource() {
      return gcsSource;
    }

    public ImportArtifactsRequest setGcsSource(GcsSource gcsSource) {
      this.gcsSource = gcsSource;
      return this;
    }
  }

  static final class GcsSource extends GenericJson {

    @Key
    private List<String> uris;

    public List<String> getUris() {
      return uris;
    }

    public GcsSource setUris(List<String> uris) {
      this.uris = uris;
      return this;
    }
  }
}

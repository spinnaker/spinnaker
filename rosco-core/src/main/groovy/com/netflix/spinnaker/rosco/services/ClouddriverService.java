package com.netflix.spinnaker.rosco.services;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.PUT;

public interface ClouddriverService {
  @PUT("/artifacts/fetch/")
  Response fetchArtifact(@Body Artifact artifact);
}

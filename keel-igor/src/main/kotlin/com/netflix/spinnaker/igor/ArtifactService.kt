package com.netflix.spinnaker.igor

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import retrofit2.http.GET
import retrofit2.http.Path

interface ArtifactService {

  @GET("/artifacts/rocket/{application}/{version}")
  suspend fun getArtifact(
    @Path("application") application: String,
    @Path("version") version: String
  ): Artifact
}

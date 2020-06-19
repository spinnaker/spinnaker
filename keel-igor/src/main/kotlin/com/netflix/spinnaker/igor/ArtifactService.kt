package com.netflix.spinnaker.igor

import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ArtifactService {

  @GET("/artifacts/rocket/{packageName}/{version}")
  suspend fun getArtifact(
    @Path("packageName") packageName: String,
    @Path("version") version: String
  ): PublishedArtifact

  @GET("/artifacts/rocket/{packageName}")
  suspend fun getVersions(
    @Path("packageName") packageName: String,
    @Query("releaseStatus") releaseStatus: List<String> = enumValues<ArtifactStatus>().toList().map { it.toString() }
  ): List<String> // sorted in descending order
}

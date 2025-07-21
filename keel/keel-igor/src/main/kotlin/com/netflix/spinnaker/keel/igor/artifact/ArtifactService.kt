package com.netflix.spinnaker.keel.igor.artifact

import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ArtifactService {

  @GET("artifacts/rocket/{packageName}/{version}")
  suspend fun getArtifact(
    @Path("packageName", encoded = true) packageName: String,
    @Path("version") version: String,
    @Query("type") artifactType: String
  ): PublishedArtifact

  @GET("artifacts/rocket/{packageName}")
  suspend fun getVersions(
    @Path("packageName", encoded = true) packageName: String,
    @Query("releaseStatus") releaseStatus: List<String> = enumValues<ArtifactStatus>().toList().map { it.toString() },
    @Query("type") artifactType: String
  ): List<String> // sorted in descending order
}

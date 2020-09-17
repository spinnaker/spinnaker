package com.netflix.spinnaker.igor
import com.netflix.spinnaker.model.Build
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface BuildService {

  /**
   * Retrieves build information by commit id and build number from a ci service.
   *
   * @param projectKey The "project" within the SCM system where the repository exists, which can be a user's personal
   *        area (e.g. "SPKR", "~<username>")
   * @param repoSlug The repository name (e.g. "myapp")
   * @param commitId The commit id.
   * @param buildNumber the build number .
   */
  @GET("/ci/builds")
  @Headers("Accept: application/json")
  suspend fun getArtifactMetadata(
    @Query("commitId") commitId: String,
    @Query("buildNumber") buildNumber: String,
    @Query("projectKey") projectKey: String? = null,
    @Query("repoSlug") repoSlug: String? = null,
    @Query("completionStatus") completionStatus: String? = null
  ): List<Build>?
}

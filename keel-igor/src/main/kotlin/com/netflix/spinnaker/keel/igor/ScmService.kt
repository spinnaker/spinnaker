package com.netflix.spinnaker.keel.igor

import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.igor.model.Branch
import com.netflix.spinnaker.keel.igor.model.BuildResult
import com.netflix.spinnaker.keel.igor.model.Comment
import kotlinx.coroutines.runBlocking
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class RawDeliveryConfigResult(
  val manifest: String
)

/**
 * Igor methods related to Source Control Management (SCM) operations.
 */
interface ScmService: ScmInfo {
  /**
   * Retrieves a delivery config manifest from a source control repository.
   *
   * @param repoType The type of SCM repository (e.g. "stash", "github")
   * @param projectKey The "project" within the SCM system where the repository exists, which can be a user's personal
   *        area (e.g. "SPKR", "~lpollo")
   * @param repositorySlug The repository name (e.g. "myapp")
   * @param manifestPath The path of the manifest file, relative to the base-path configured by the Spinnaker operator
   *        in igor (which defaults to ".spinnaker"), for example "mydir/spinnaker.yml". The full path to the file
   *        is determined by concatenating the base path with this relative path (e.g. ".spinnaker/mydir/spinnaker.yml").
   * @param ref The git reference at which to retrieve to file (e.g. a commit hash, or a reference like "refs/heads/mybranch").
   * @param raw returns the config as string if true, otherwise parses and converts the string to a map
   */
  @GET("/delivery-config/manifest")
  suspend fun getDeliveryConfigManifest(
    @Query("scmType") repoType: String,
    @Query("project") projectKey: String,
    @Query("repository") repositorySlug: String,
    @Query("manifest") manifestPath: String,
    @Query("ref") ref: String? = null,
    @Query("raw") raw: Boolean = true,
    ): RawDeliveryConfigResult

  /**
   * Retrieves all SCM base links, as defined in Igor
   */
  @GET("/scm/masters")
  override suspend fun getScmInfo(): Map<String, String?>

  /**
   * Returns the default [Branch] for the specified repo.
   */
  @GET("/scm/repos/{scmType}/{projectKey}/{repoSlug}/branches/default")
  suspend fun getDefaultBranch(
    @Path("scmType") scmType: String,
    @Path("projectKey") projectKey: String,
    @Path("repoSlug") repoSlug: String
  ): Branch

  @POST("/scm/repos/{scmType}/{projectKey}/{repoSlug}/pull-requests/{pullRequestId}/comments")
  suspend fun commentOnPullRequest(
    @Path("scmType") scmType: String,
    @Path("projectKey") projectKey: String,
    @Path("repoSlug") repoSlug: String,
    @Path("pullRequestId") pullRequestId: String,
    @Body comment: Comment
  )

  @POST("/scm/build-results/{scmType}/{commitHash}")
  suspend fun postBuildResultToCommit(
    @Path("scmType") scmType: String,
    @Path("commitHash") commitHash: String,
    @Body buildResult: BuildResult
  ): Response<Unit>
}

fun Application.getDefaultBranch(scmService: ScmService): String = runBlocking {
  scmService.getDefaultBranch(
    scmType = repoType ?: error("Missing SCM type in config for application $name"),
    projectKey = repoProjectKey ?: error("Missing SCM project in config for application $name"),
    repoSlug = repoSlug ?: error("Missing SCM repository in config for application $name")
  ).name
}


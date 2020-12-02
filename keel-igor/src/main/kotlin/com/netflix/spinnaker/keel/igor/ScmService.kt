package com.netflix.spinnaker.keel.igor

import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Igor methods related to Source Control Management (SCM) operations.
 */
interface ScmService: ScmInfo
{
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
   */
  @GET("/delivery-config/manifest")
  suspend fun getDeliveryConfigManifest(
    @Query("scmType") repoType: String,
    @Query("project") projectKey: String,
    @Query("repository") repositorySlug: String,
    @Query("manifest") manifestPath: String,
    @Query("ref") ref: String? = null
  ): SubmittedDeliveryConfig

  /**
   * Retrieves all SCM base links, as defined in Igor
   */
  @GET("/scm/masters")
  override suspend fun getScmInfo(): Map<String, String?>
}

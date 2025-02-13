package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface IgorService {
  @GET("/masters")
  Call<List<String>> getBuildMasters();

  /**
   * Get build masters
   *
   * @param type - optional parameter the (non-case-sensitive) build service type name (e.g.
   *     Jenkins)
   * @return
   */
  @GET("/masters")
  Call<List<String>> getBuildMasters(@Query("type") String type);

  @GET("/jobs/{buildMaster}")
  Call<List<String>> getJobsForBuildMaster(@Path("buildMaster") String buildMaster);

  @GET("/jobs/{buildMaster}/{job}")
  Call<Map> getJobConfig(
      @Path("buildMaster") String buildMaster, @Path(value = "job", encoded = true) String job);

  @GET("/builds/all/{buildMaster}/{job}")
  Call<List<Map>> getBuilds(
      @Path("buildMaster") String buildMaster, @Path(value = "job", encoded = true) String job);

  @GET("/builds/status/{number}/{buildMaster}/{job}")
  Call<Map> getBuild(
      @Path("buildMaster") String buildMaster,
      @Path(value = "job", encoded = true) String job,
      @Path("number") String number);

  @GET("/artifactory/names")
  Call<List<String>> getArtifactoryNames();

  @GET("/nexus/names")
  Call<List<String>> getNexusNames();

  @GET("/concourse/{buildMaster}/teams")
  Call<List<String>> getConcourseTeams(@Path("buildMaster") String buildMaster);

  @GET("/concourse/{buildMaster}/teams/{team}/pipelines")
  Call<List<String>> getConcoursePipelines(
      @Path("buildMaster") String buildMaster, @Path("team") String team);

  @GET("/concourse/{buildMaster}/teams/{team}/pipelines/{pipeline}/jobs")
  Call<List<String>> getConcourseJobs(
      @Path("buildMaster") String buildMaster,
      @Path("team") String team,
      @Path("pipeline") String pipeline);

  @GET("/gcb/accounts")
  Call<List<String>> getGoogleCloudBuildAccounts();

  @GET("/gcb/triggers/{account}")
  Call<List<GoogleCloudBuildTrigger>> getGoogleCloudBuildTriggers(@Path("account") String account);

  @GET("/codebuild/accounts")
  Call<List<String>> getAwsCodeBuildAccounts();

  @GET("/codebuild/projects/{account}")
  Call<List<String>> getAwsCodeBuildProjects(@Path("account") String account);

  @GET("/artifacts/{provider}/{packageName}")
  Call<List<String>> getArtifactVersions(
      @Path("provider") String provider,
      @Path("packageName") String packageName,
      @Query("releaseStatus") String releaseStatus);

  @GET("/artifacts/{provider}/{packageName}/{version}")
  Call<Map<String, Object>> getArtifactByVersion(
      @Path("provider") String provider,
      @Path("packageName") String packageName,
      @Path("version") String version);

  @GET("/concourse/{buildMaster}/teams/{team}/pipelines/{pipeline}/resources")
  Call<List<String>> getConcourseResources(
      @Path("buildMaster") String buildMaster,
      @Path("team") String team,
      @Path("pipeline") String pipeline);

  @GET("/ci/builds")
  Call<List<Map<String, Object>>> getBuilds(
      @Query("projectKey") String projectKey,
      @Query("repoSlug") String repoSlug,
      @Query("completionStatus") String completionStatus,
      @Query("buildNumber") String buildNumber,
      @Query("commitId") String commitId);

  @GET("/ci/builds/{buildId}/output")
  Call<Map<String, Object>> getBuildOutput(@Path("buildId") String buildId);

  @GET("/installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();
}

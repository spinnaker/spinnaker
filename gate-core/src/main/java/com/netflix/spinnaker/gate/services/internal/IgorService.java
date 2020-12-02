package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import retrofit.http.EncodedPath;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;

public interface IgorService {
  @GET("/masters")
  List<String> getBuildMasters();

  /**
   * Get build masters
   *
   * @param type - optional parameter the (non-case-sensitive) build service type name (e.g.
   *     Jenkins)
   * @return
   */
  @GET("/masters")
  List<String> getBuildMasters(@Query("type") String type);

  @GET("/jobs/{buildMaster}")
  List<String> getJobsForBuildMaster(@Path("buildMaster") String buildMaster);

  @GET("/jobs/{buildMaster}/{job}")
  Map getJobConfig(@Path("buildMaster") String buildMaster, @EncodedPath("job") String job);

  @GET("/builds/all/{buildMaster}/{job}")
  List<Map> getBuilds(@Path("buildMaster") String buildMaster, @EncodedPath("job") String job);

  @GET("/builds/status/{number}/{buildMaster}/{job}")
  Map getBuild(
      @Path("buildMaster") String buildMaster,
      @EncodedPath("job") String job,
      @Path("number") String number);

  @GET("/artifactory/names")
  List<String> getArtifactoryNames();

  @GET("/nexus/names")
  List<String> getNexusNames();

  @GET("/concourse/{buildMaster}/teams")
  List<String> getConcourseTeams(@Path("buildMaster") String buildMaster);

  @GET("/concourse/{buildMaster}/teams/{team}/pipelines")
  List<String> getConcoursePipelines(
      @Path("buildMaster") String buildMaster, @Path("team") String team);

  @GET("/concourse/{buildMaster}/teams/{team}/pipelines/{pipeline}/jobs")
  List<String> getConcourseJobs(
      @Path("buildMaster") String buildMaster,
      @Path("team") String team,
      @Path("pipeline") String pipeline);

  @GET("/gcb/accounts")
  List<String> getGoogleCloudBuildAccounts();

  @GET("/gcb/triggers/{account}")
  List<GoogleCloudBuildTrigger> getGoogleCloudBuildTriggers(@Path("account") String account);

  @GET("/codebuild/accounts")
  List<String> getAwsCodeBuildAccounts();

  @GET("/codebuild/projects/{account}")
  List<String> getAwsCodeBuildProjects(@Path("account") String account);

  @GET("/artifacts/{provider}/{packageName}")
  List<String> getArtifactVersions(
      @Path("provider") String provider,
      @Path("packageName") String packageName,
      @Query("releaseStatus") String releaseStatus);

  @GET("/artifacts/{provider}/{packageName}/{version}")
  Map<String, Object> getArtifactByVersion(
      @Path("provider") String provider,
      @Path("packageName") String packageName,
      @Path("version") String version);

  @GET("/concourse/{buildMaster}/teams/{team}/pipelines/{pipeline}/resources")
  List<String> getConcourseResources(
      @Path("buildMaster") String buildMaster,
      @Path("team") String team,
      @Path("pipeline") String pipeline);

  @GET("/ci/builds")
  List<Map<String, Object>> getBuilds(
      @Query("projectKey") String projectKey,
      @Query("repoSlug") String repoSlug,
      @Query("completionStatus") String completionStatus,
      @Query("buildNumber") String buildNumber,
      @Query("commitId") String commitId);

  @GET("/ci/builds/{buildId}/output")
  Map<String, Object> getBuildOutput(@Path("buildId") String buildId);

  @GET("/installedPlugins")
  List<SpinnakerPluginDescriptor> getInstalledPlugins();
}

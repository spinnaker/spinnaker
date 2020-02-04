/*
 * Copyright 2014 Netflix, Inc.
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.gate.services.internal

import com.fasterxml.jackson.annotation.JsonProperty
import retrofit.http.EncodedPath
import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query

interface IgorService {
  /*
   * Job names can have '/' in them if using the Jenkins Folder plugin.
   * Because of this, always put the job name at the end of the URL.
   */

  @GET('/masters')
  List<String> getBuildMasters()

  /**
   * Get build masters
   * @param type - optional parameter the (non-case-sensitive) build service type name (e.g. Jenkins)
   * @return
   */
  @GET('/masters')
  List<String> getBuildMasters(@Query("type") String type)

  @GET('/jobs/{buildMaster}')
  List<String> getJobsForBuildMaster(@Path("buildMaster") String buildMaster)

  @GET('/jobs/{buildMaster}/{job}')
  Map getJobConfig(@Path("buildMaster") String buildMaster, @EncodedPath("job") String job)

  @GET('/builds/all/{buildMaster}/{job}')
  List<Map> getBuilds(@Path("buildMaster") String buildMaster, @EncodedPath("job") String job)

  @GET('/builds/status/{number}/{buildMaster}/{job}')
  Map getBuild(@Path("buildMaster") String buildMaster, @EncodedPath("job") String job, @Path("number") String number)

  @GET('/artifactory/names')
  List<String> getArtifactoryNames()

  @GET('/nexus/names')
  List<String> getNexusNames()

  @GET('/concourse/{buildMaster}/teams')
  List<String> getConcourseTeams(@Path("buildMaster") String buildMaster)

  @GET('/concourse/{buildMaster}/teams/{team}/pipelines')
  List<String> getConcoursePipelines(@Path("buildMaster") String buildMaster, @Path("team") String team);

  @GET('/concourse/{buildMaster}/teams/{team}/pipelines/{pipeline}/jobs')
  List<String> getConcourseJobs(@Path("buildMaster") String buildMaster, @Path("team") String team, @Path("pipeline") String pipeline);

  @GET('/gcb/accounts')
  List<String> getGoogleCloudBuildAccounts();

  @GET('/gcb/triggers/{account}')
  List<GoogleCloudBuildTrigger> getGoogleCloudBuildTriggers(@Path("account") String account);

  @GET('/codebuild/accounts')
  List<String> getAwsCodeBuildAccounts();

  @GET('/artifacts/{provider}/{packageName}')
  List<String> getArtifactVersions(@Path("provider") String provider,
                                   @Path("packageName") String packageName,
                                   @Query("releaseStatus") String releaseStatus);

  @GET('/artifacts/{provider}/{packageName}/{version}')
  Map<String, Object> getArtifactByVersion(@Path("provider") String provider,
                                           @Path("packageName") String packageName,
                                           @Path("version") String version);

  @GET('/concourse/{buildMaster}/teams/{team}/pipelines/{pipeline}/resources')
  List<String> getConcourseResources(@Path("buildMaster") String buildMaster, @Path("team") String team, @Path("pipeline") String pipeline);

}

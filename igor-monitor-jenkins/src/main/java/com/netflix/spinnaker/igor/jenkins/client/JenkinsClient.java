/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.jenkins.client;

import com.netflix.spinnaker.igor.jenkins.client.model.*;
import java.util.Map;
import retrofit.client.Response;
import retrofit.http.*;

/** Interface for interacting with a Jenkins Service via Xml */
@SuppressWarnings("LineLength")
public interface JenkinsClient {
  @GET(
      "/api/xml?tree=jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url]]]]]]]]]]]&exclude=/*/*/*/action[not(totalCount)]")
  public abstract ProjectsList getProjects();

  @GET(
      "/api/xml?tree=jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name]]]]]]]]]]")
  public abstract JobList getJobs();

  @GET(
      "/job/{jobName}/api/xml?exclude=/*/build/action[not(totalCount)]&tree=builds[number,url,duration,timestamp,result,building,url,fullDisplayName,actions[failCount,skipCount,totalCount]]")
  public abstract BuildsList getBuilds(@EncodedPath("jobName") String jobName);

  @GET(
      "/job/{jobName}/api/xml?tree=name,url,actions[processes[name]],downstreamProjects[name,url],upstreamProjects[name,url]")
  public abstract BuildDependencies getDependencies(@EncodedPath("jobName") String jobName);

  @GET(
      "/job/{jobName}/{buildNumber}/api/xml?exclude=/*/action[not(totalCount)]&tree=actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url,fullDisplayName,artifacts[displayPath,fileName,relativePath]")
  public abstract Build getBuild(
      @EncodedPath("jobName") String jobName, @Path("buildNumber") Integer buildNumber);

  @GET(
      "/job/{jobName}/{buildNumber}/api/xml?exclude=/*/action[not(build|lastBuiltRevision)]&tree=actions[remoteUrls,lastBuiltRevision[branch[name,SHA1]],build[revision[branch[name,SHA1]]]]")
  public abstract ScmDetails getGitDetails(
      @EncodedPath("jobName") String jobName, @Path("buildNumber") Integer buildNumber);

  @GET("/job/{jobName}/lastCompletedBuild/api/xml")
  public abstract Build getLatestBuild(@EncodedPath("jobName") String jobName);

  @GET("/queue/item/{itemNumber}/api/xml")
  public abstract QueuedJob getQueuedItem(@Path("itemNumber") Integer item);

  @POST("/job/{jobName}/build")
  public abstract Response build(
      @EncodedPath("jobName") String jobName,
      @Body String emptyRequest,
      @Header("Jenkins-Crumb") String crumb);

  @POST("/job/{jobName}/buildWithParameters")
  public abstract Response buildWithParameters(
      @EncodedPath("jobName") String jobName,
      @QueryMap Map<String, String> queryParams,
      @Body String EmptyRequest,
      @Header("Jenkins-Crumb") String crumb);

  @POST("/job/{jobName}/{buildNumber}/stop")
  public abstract Response stopRunningBuild(
      @EncodedPath("jobName") String jobName,
      @Path("buildNumber") Integer buildNumber,
      @Body String EmptyRequest,
      @Header("Jenkins-Crumb") String crumb);

  @POST("/queue/cancelItem")
  public abstract Response stopQueuedBuild(
      @Query("id") String queuedBuild,
      @Body String emptyRequest,
      @Header("Jenkins-Crumb") String crumb);

  @GET(
      "/job/{jobName}/api/xml?exclude=/*/action&exclude=/*/build&exclude=/*/property[not(parameterDefinition)]")
  public abstract JobConfig getJobConfig(@EncodedPath("jobName") String jobName);

  @Streaming
  @GET("/job/{jobName}/{buildNumber}/artifact/{fileName}")
  public abstract Response getPropertyFile(
      @EncodedPath("jobName") String jobName,
      @Path("buildNumber") Integer buildNumber,
      @Path(value = "fileName", encode = false) String fileName);

  @GET("/crumbIssuer/api/xml")
  public abstract Crumb getCrumb();
}

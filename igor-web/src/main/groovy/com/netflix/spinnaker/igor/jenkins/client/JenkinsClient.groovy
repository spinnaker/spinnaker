/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.jenkins.client

import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildDependencies
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.client.model.JobList
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.jenkins.client.model.QueuedJob
import com.netflix.spinnaker.igor.jenkins.client.model.ScmDetails
import retrofit.client.Response
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.QueryMap
import retrofit.http.Streaming

/**
 * Interface for interacting with a Jenkins Service via Xml
 */
@SuppressWarnings('LineLength')
interface JenkinsClient {

    @GET('/api/xml?tree=jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url]]&exclude=/*/*/*/action[not(totalCount)]')
    ProjectsList getProjects()

    @GET('/api/xml?tree=jobs[name]')
    JobList getJobs()

    @GET('/job/{jobName}/api/xml?exclude=/*/build/action[not(totalCount)]&tree=builds[number,url,duration,timestamp,result,building,url,fullDisplayName,actions[failCount,skipCount,totalCount]]')
    BuildsList getBuilds(@Path('jobName') String jobName)

    @GET('/job/{jobName}/api/xml?tree=name,url,actions[processes[name]],downstreamProjects[name,url],upstreamProjects[name,url]')
    BuildDependencies getDependencies(@Path('jobName') String jobName)

    @GET('/job/{jobName}/{buildNumber}/api/xml?exclude=/*/action[not(totalCount)]&tree=actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url,fullDisplayName,artifacts[displayPath,fileName,relativePath]')
    Build getBuild(@Path('jobName') String jobName, @Path('buildNumber') Integer buildNumber)

    @GET('/job/{jobName}/{buildNumber}/api/xml?exclude=/*/action[not(lastBuiltRevision)]&tree=actions[remoteUrl,lastBuiltRevision[branch[name,SHA1]]]')
    ScmDetails getGitDetails(@Path('jobName') String jobName, @Path('buildNumber') Integer buildNumber)

    @GET('/job/{jobName}/lastCompletedBuild/api/xml')
    Build getLatestBuild(@Path('jobName') String jobName)

    @GET('/queue/item/{itemNumber}/api/xml')
    QueuedJob getQueuedItem(@Path('itemNumber') Integer item)

    @POST('/job/{jobName}/build')
    Response build(@Path('jobName') String jobName)

    @POST('/job/{jobName}/buildWithParameters')
    Response buildWithParameters(@Path('jobName') String jobName, @QueryMap Map<String, String> queryParams)

    @GET('/job/{jobName}/api/xml?exclude=/*/action&exclude=/*/build&exclude=/*/property[not(parameterDefinition)]')
    JobConfig getJobConfig(@Path('jobName') String jobName)

    @Streaming
    @GET('/job/{jobName}/{buildNumber}/artifact/{fileName}')
    Response getPropertyFile(
        @Path('jobName') String jobName,
        @Path('buildNumber') Integer buildNumber, @Path(value = 'fileName', encode = false) String fileName)

}

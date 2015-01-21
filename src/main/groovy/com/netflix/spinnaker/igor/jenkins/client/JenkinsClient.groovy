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
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifactList
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.jenkins.client.model.BuildDependencies
import com.netflix.spinnaker.igor.jenkins.client.model.QueuedJob
import retrofit.client.Response
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path

/**
 * Interface for interacting with a Jenkins Service via Xml
 */
@SuppressWarnings('LineLength')
interface JenkinsClient {

    @GET('/view/All/cc.xml')
    ProjectsList getProjects()

    @GET('/job/{jobName}/api/xml?tree=builds[number,url,duration,timestamp,result,culprits[fullName],actions[failCount,skipCount,totalCount,causes[*]]]')
    List<Build> getBuilds(@Path('jobName') String jobName)

    @GET('/job/{jobName}/api/xml?tree=name,url,actions[processes[name]],downstreamProjects[name,url],upstreamProjects[name,url]')
    BuildDependencies getDependencies(@Path('jobName') String jobName)

    @GET('/job/{jobName}/{buildNumber}/api/xml')
    Build getBuild(@Path('jobName') String jobName, @Path('buildNumber') Integer buildNumber)

    @GET('/job/{jobName}/lastCompletedBuild/api/xml')
    Build getLatestBuild(@Path('jobName') String jobName)

    @GET('/queue/item/{itemNumber}/api/xml')
    QueuedJob getQueuedItem(@Path('itemNumber') Integer item)

    @POST('/job/{jobName}/build')
    Response build(@Path('jobName') String jobName)

    @GET('/job/{jobName}/{buildNumber}/api/xml?tree=artifacts[displayPath,fileName,relativePath]')
    BuildArtifactList getArtifacts(@Path('jobName') String jobName, @Path('buildNumber') Integer buildNumber)
}

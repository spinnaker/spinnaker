/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.igor.jenkins.service

import com.netflix.spinnaker.hystrix.SimpleHystrixCommand
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildDependencies
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.client.model.JobList
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.jenkins.client.model.QueuedJob
import com.netflix.spinnaker.igor.jenkins.client.model.ScmDetails
import retrofit.client.Response

class JenkinsService {
    final String groupKey
    final JenkinsClient jenkinsClient

    JenkinsService(String jenkinsHostId, JenkinsClient jenkinsClient) {
        this.groupKey = "jenkins-${jenkinsHostId}"
        this.jenkinsClient = jenkinsClient
    }

    ProjectsList getProjects() {
        new SimpleHystrixCommand<ProjectsList>(
            groupKey, "getProjects", {
            return jenkinsClient.getProjects()
        }).execute()
    }

    JobList getJobs() {
        new SimpleHystrixCommand<JobList>(
            groupKey, "getJobs", {
            return jenkinsClient.getJobs()
        }).execute()
    }

    BuildsList getBuilds(String jobName) {
        new SimpleHystrixCommand<BuildsList>(
            groupKey, "getBuilds", {
            return jenkinsClient.getBuilds(jobName)
        }).execute()
    }

    BuildDependencies getDependencies(String jobName) {
        new SimpleHystrixCommand<BuildDependencies>(
            groupKey, "getDependencies", {
            return jenkinsClient.getDependencies(jobName)
        }).execute()
    }

    Build getBuild(String jobName, Integer buildNumber) {
        new SimpleHystrixCommand<Build>(
            groupKey, "getBuild", {
            return jenkinsClient.getBuild(jobName, buildNumber)
        }).execute()
    }

    ScmDetails getGitDetails(String jobName, Integer buildNumber) {
        new SimpleHystrixCommand<ScmDetails>(
            groupKey, "getGitDetails", {
            return jenkinsClient.getGitDetails(jobName, buildNumber)
        }).execute()
    }

    Build getLatestBuild(String jobName) {
        new SimpleHystrixCommand<Build>(
            groupKey, "getLatestBuild", {
            return jenkinsClient.getLatestBuild(jobName)
        }).execute()
    }

    QueuedJob getQueuedItem(Integer item) {
        return jenkinsClient.getQueuedItem(item)
    }

    Response build(String jobName) {
        return jenkinsClient.build(jobName)
    }

    Response buildWithParameters(String jobName, Map<String, String> queryParams) {
        return jenkinsClient.buildWithParameters(jobName, queryParams)
    }

    JobConfig getJobConfig(String jobName) {
        return jenkinsClient.getJobConfig(jobName)
    }

    Response getPropertyFile(String jobName, Integer buildNumber, String fileName) {
        new SimpleHystrixCommand<Response>(
            groupKey, "getPropertyFile", {
            return jenkinsClient.getPropertyFile(jobName, buildNumber, fileName)
        }).execute()

    }
}

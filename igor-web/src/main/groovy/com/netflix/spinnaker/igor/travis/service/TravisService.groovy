/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.service

import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.build.model.GenericJobConfiguration
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildService
import com.netflix.spinnaker.igor.travis.TravisCache
import com.netflix.spinnaker.igor.travis.client.TravisClient
import com.netflix.spinnaker.igor.travis.client.logparser.ArtifactParser
import com.netflix.spinnaker.igor.travis.client.logparser.PropertyParser
import com.netflix.spinnaker.igor.travis.client.model.AccessToken
import com.netflix.spinnaker.igor.travis.client.model.Accounts
import com.netflix.spinnaker.igor.travis.client.model.Build
import com.netflix.spinnaker.igor.travis.client.model.Builds
import com.netflix.spinnaker.igor.travis.client.model.Job
import com.netflix.spinnaker.igor.travis.client.model.Jobs
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.client.model.RepoRequest
import com.netflix.spinnaker.igor.travis.client.model.Repos
import com.netflix.spinnaker.igor.travis.client.model.TriggerResponse
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import retrofit.client.Response
import retrofit.mime.TypedByteArray

@Slf4j
class TravisService implements BuildService {
    final String baseUrl
    final String groupKey
    final String githubToken
    final TravisClient travisClient
    final TravisCache travisCache
    private AccessToken accessToken
    private Accounts accounts

    TravisService(String travisHostId, String baseUrl, String githubToken, TravisClient travisClient, TravisCache travisCache) {
        this.groupKey     = "travis-${travisHostId}"
        this.githubToken  = githubToken
        this.travisClient = travisClient
        this.baseUrl      = baseUrl
        this.travisCache  = travisCache
    }

    void setAccessToken() {
        this.accessToken = travisClient.accessToken(githubToken)
    }

    String getAccessToken() {
        if (!accessToken) {
            setAccessToken()
        }
        return "token " + accessToken.accessToken
    }

    Accounts getAccounts() {
        this.accounts = travisClient.accounts(getAccessToken())
        log.debug "fetched " + accounts.accounts.size() + " accounts"
        accounts.accounts.each {
            log.debug "account: " + it.login
            log.debug "repos:" + it.reposCount
        }
        return accounts
    }

    List<Build> getBuilds() {
        Builds builds = travisClient.builds(getAccessToken())
        log.debug "fetched " + builds.builds.size() + " builds"
        return builds.builds
    }

    Build getBuild(Repo repo, int buildNumber) {
        return travisClient.build(getAccessToken(), repo.id, buildNumber)
    }

    Builds getBuilds(String repoSlug, int buildNumber) {
        return travisClient.builds(getAccessToken(), repoSlug, buildNumber)
    }

    Build getBuild(String repoSlug, int buildNumber) {
        Builds builds = getBuilds(repoSlug, buildNumber)
        return builds.builds.size() > 0 ? builds.builds.first() : null
    }

    @Override
    GenericBuild getGenericBuild(String repoSlug, int buildNumber) {
        Build build = getBuild(repoSlug, buildNumber)
        GenericBuild genericBuild = getGenericBuild(build, repoSlug)
        return genericBuild
    }

    @Override
    List<GenericGitRevision> getGenericGitRevisions(String repoSlug, int buildNumber) {
        Builds builds = getBuilds(repoSlug, buildNumber)
        if (builds.commits?.branch) {
            return builds.commits*.genericGitRevision()
        }
        return null
    }

    Map<String, Object> getBuildProperties(String repoSlug, int buildNumber) {
        Build build = getBuild(repoSlug, buildNumber)
        return PropertyParser.extractPropertiesFromLog(getLog(build))
    }

    List<Build> getBuilds(Repo repo) {
        Builds builds = travisClient.builds(getAccessToken(), repo.id)
        log.debug "fetched " + builds.builds.size() + " builds"
        return builds.builds
    }

    List<GenericBuild> getBuilds(String repoSlug) {
        Builds builds = travisClient.builds(getAccessToken(), repoSlug)
        List<GenericBuild> list = new ArrayList<GenericBuild>()
        builds.builds.each { build ->
            list.add getGenericBuild(build, repoSlug)
        }
        return list
    }

    List<Repo> getRepos() {
        Repos repos = travisClient.repos(getAccessToken())
        log.debug "fetched " + repos.repos.size() + " repos"
        return repos.repos
    }

    List<Repo> getReposForAccounts() {
        log.debug "fetching repos for relevant accounts only"
        List<Repo> repos = []
        accounts.accounts.each { account ->
            Repos accountRepos = travisClient.repos(getAccessToken(), account.login)
            accountRepos.repos.each { repo ->
                log.debug "[${account.login}] [${repo.slug}]"
            }
            repos.addAll accountRepos.repos
        }
        return repos
    }

    Job getJob(int jobId) {
        log.debug "fetching job for ${jobId}"
        Jobs jobs = travisClient.jobs(getAccessToken(), jobId)
        return jobs.job
    }

    String getLog(Build build) {
        String buildLog = ""
        build.job_ids.each {
            Job job = getJob(it.intValue())
            buildLog += getLog(job.logId)
        }
        return buildLog
    }

    String getLog(int logId) {
        log.debug "fetching log for ${logId}"
        Response response = travisClient.log(getAccessToken(), logId)
        String job_log = new String(((TypedByteArray) response.getBody()).getBytes());
        return job_log
    }

    Repo getRepo(int repositoryId) {
        return travisClient.repo(getAccessToken(), repositoryId)
    }

    Repo getRepo(String repoSlug) {
        return travisClient.repoWrapper(getAccessToken(), repoSlug).repo
    }

    GenericBuild getGenericBuild(Build build, String repoSlug) {
        GenericBuild genericBuild = TravisBuildConverter.genericBuild(build, repoSlug, baseUrl)
        genericBuild.artifacts = ArtifactParser.getArtifactsFromLog(getLog(build))
        return genericBuild
    }

    GenericJobConfiguration getJobConfig(String repoSlug) {
        Builds builds = travisClient.builds(getAccessToken(), repoSlug)
        Job job = getJob(builds.builds.first().job_ids.first())
        return new GenericJobConfiguration(job.repositorySlug.split('/').last(),job.repositorySlug.split('/').last(), job.repositorySlug ,false, getUrl(job.repositorySlug),false)
    }

    String getUrl(String repoSlug) {
        return "${baseUrl}/${repoSlug}"
    }

    int triggerBuild (String repoSlug) {
        Repo repo = getRepo(repoSlug)
        RepoRequest repoRequest = new RepoRequest("master")
        TriggerResponse triggerResponse = travisClient.triggerBuild(getAccessToken(), repoSlug, repoRequest)
        if (triggerResponse.remainingRequests) {
            log.info "${groupKey}: remaining requests: ${triggerResponse.remainingRequests}"
        }
        return travisCache.setQueuedJob(groupKey, repoSlug, repo.lastBuildNumber+1)
    }

    Map<String, Integer> queuedBuild(int queueId) {
        Map queuedJob = travisCache.getQueuedJob(groupKey, queueId)
        Build build = getBuild(queuedJob.jobName, queuedJob.buildNumber)
        if (build) {
            log.info "removing ${queueId} from ${groupKey} travisCache"
            travisCache.remove(groupKey, queueId)
            return ["number":build.number]
        }
        return null
    }

    @Override
    BuildServiceProvider buildServiceProvider() {
        return BuildServiceProvider.TRAVIS
    }
}

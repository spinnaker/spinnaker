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

import com.netflix.spinnaker.hystrix.SimpleHystrixCommand
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
import com.netflix.spinnaker.igor.travis.client.model.Commit
import com.netflix.spinnaker.igor.travis.client.model.Config
import com.netflix.spinnaker.igor.travis.client.model.EmptyObject
import com.netflix.spinnaker.igor.travis.client.model.GithubAuth
import com.netflix.spinnaker.igor.travis.client.model.Job
import com.netflix.spinnaker.igor.travis.client.model.Jobs
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.client.model.RepoRequest
import com.netflix.spinnaker.igor.travis.client.model.Repos
import com.netflix.spinnaker.igor.travis.client.model.TriggerResponse
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Builds
import groovy.util.logging.Slf4j
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedByteArray

@Slf4j
class TravisService implements BuildService {
    final String baseUrl
    final String groupKey
    final GithubAuth gitHubAuth
    final TravisClient travisClient
    final TravisCache travisCache
    final private Set<String> artifactRegexes
    protected AccessToken accessToken
    private Accounts accounts

    TravisService(String travisHostId, String baseUrl, String githubToken, TravisClient travisClient, TravisCache travisCache, Iterable<String> artifactRegexes) {
        this.groupKey     = "${travisHostId}"
        this.gitHubAuth   = new GithubAuth(githubToken)
        this.travisClient = travisClient
        this.baseUrl      = baseUrl
        this.travisCache  = travisCache
        this.artifactRegexes = artifactRegexes == null ? Collections.EMPTY_LIST : new HashSet<>(artifactRegexes)
    }

    @Override
    BuildServiceProvider buildServiceProvider() {
        return BuildServiceProvider.TRAVIS
    }

    @Override
    List<GenericGitRevision> getGenericGitRevisions(String inputRepoSlug, int buildNumber) {
        new SimpleHystrixCommand<List<GenericGitRevision>>(
            groupKey, buildCommandKey("getGenericGitRevisions"),
            {
                String repoSlug = cleanRepoSlug(inputRepoSlug)
                Builds builds = getBuilds(repoSlug, buildNumber)
                return builds.commits?.branch ? builds.commits*.genericGitRevision() : null
            }
        ).execute()
    }

    @Override
    GenericBuild getGenericBuild(String inputRepoSlug, int buildNumber) {
        new SimpleHystrixCommand<GenericBuild>(
            groupKey, buildCommandKey("getGenericBuild"),
            {
                String repoSlug = cleanRepoSlug(inputRepoSlug)
                Build build = getBuild(repoSlug, buildNumber)
                return getGenericBuild(build, repoSlug)
            }
        ).execute()
    }

    @Override
    int triggerBuildWithParameters(String inputRepoSlug, Map<String, String> queryParameters) {
        String repoSlug = cleanRepoSlug(inputRepoSlug)
        Repo repo = getRepo(repoSlug)
        String branch = branchFromRepoSlug(inputRepoSlug)
        RepoRequest repoRequest = new RepoRequest(branch.empty? "master" : branch)

        repoRequest.config = new Config(queryParameters)

        TriggerResponse triggerResponse = travisClient.triggerBuild(getAccessToken(), repoSlug, repoRequest)
        if (triggerResponse.remainingRequests) {
            log.info "${groupKey}: remaining requests: ${triggerResponse.remainingRequests}"
        }
        return travisCache.setQueuedJob(groupKey, repoSlug, repo.lastBuildNumber+1)
    }

    List<Build> getBuilds() {
        Builds builds = travisClient.builds(getAccessToken())
        log.debug "fetched " + builds.builds.size() + " builds"
        return builds.builds
    }

    Build getBuild(Repo repo, int buildNumber) {
        new SimpleHystrixCommand<Build>(
            groupKey, buildCommandKey("getBuild"),
            {
                return travisClient.build(getAccessToken(), repo.id, buildNumber)
            }
        ).execute()
    }

    Builds getBuilds(String repoSlug, int buildNumber) {
        new SimpleHystrixCommand<Builds>(
            groupKey, buildCommandKey("getBuilds"),
            {
                return travisClient.builds(getAccessToken(), repoSlug, buildNumber)
            }
        ).execute()
    }

    Build getBuild(String repoSlug, int buildNumber) {
        Builds builds = getBuilds(repoSlug, buildNumber)
        return builds.builds.size() > 0 ? builds.builds.first() : null
    }

    Map<String, Object> getBuildProperties(String inputRepoSlug, int buildNumber) {
        String repoSlug = cleanRepoSlug(inputRepoSlug)

        Build build = getBuild(repoSlug, buildNumber)
        return PropertyParser.extractPropertiesFromLog(getLog(build))
    }

    List<Build> getBuilds(Repo repo) {
        Builds builds = travisClient.builds(getAccessToken(), repo.id)
        log.debug "fetched " + builds.builds.size() + " builds"
        return builds.builds
    }

    List<V3Build> getBuilds(Repo repo, int limit) {
        V3Builds builds = travisClient.builds(getAccessToken(), repo.id, limit)
        log.debug "fetched ${builds.builds.size()} builds"
        return builds.builds
    }

    List<GenericBuild> getBuilds(String inputRepoSlug) {
        String repoSlug = cleanRepoSlug(inputRepoSlug)
        String branch = branchFromRepoSlug(inputRepoSlug)
        boolean tagsVirtualBranch = branchIsTagsVirtualBranch(inputRepoSlug)
        Builds builds = travisClient.builds(getAccessToken(), repoSlug)
        List<GenericBuild> list = new ArrayList<GenericBuild>()
        builds.builds.each { build ->
            if (tagsVirtualBranch) {
                Commit commit = builds.commits.find{
                    it.id == build.commitId
                }
                if (commit?.isTag()) {
                    list.add(getGenericBuild(build, repoSlug))
                }
            } else if (branch.length() == 0) {
                list.add(getGenericBuild(build, repoSlug))
            } else {
                Commit commit = builds.commits.find{
                    it.id == build.commitId
                }
                if (!commit) {
                    log.info("${groupKey}:${repoSlug}:${build.number} - Could not find commit for build.")
                    list.add(getGenericBuild(build, repoSlug))
                } else if (commit.branch.equalsIgnoreCase(branch) && build.pullRequest == branchIsPullRequestVirtualBranch(inputRepoSlug)){
                    list.add(getGenericBuild(build, repoSlug))
                }
            }
        }
        return list
    }

    Commit getCommit(String repoSlug, int buildNumber) {
        Builds builds = getBuilds(repoSlug, buildNumber)
        if (builds?.commits) {
            return builds.commits.first()
        }
        throw new NoSuchFieldException("No commit found for ${repoSlug}:${buildNumber}")
    }


    List<Repo> getReposForAccounts() {
        new SimpleHystrixCommand<List<Repo>>(
            groupKey, buildCommandKey("getReposForAccounts"),
            {
                log.debug "fetching repos for relevant accounts only"
                List<Repo> repos = []
                getAccounts().accounts.each { account ->
                    Repos accountRepos = travisClient.repos(getAccessToken(), account.login)
                    accountRepos.repos.each { repo ->
                        log.debug "[${account.login}] [${repo.slug}]"
                    }
                    repos.addAll accountRepos.repos
                }
                return repos
            },
            {
                return []
            }
        ).execute()
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
            if (job.logId) {
                buildLog += getLog(job.logId)
            } else {
                buildLog += getJobLog(job.id)
            }
        }
        return buildLog
    }

    String getLog(int logId) {
        log.debug "fetching log by logId ${logId}"
        Response response = travisClient.log(getAccessToken(), logId)
        String job_log = new String(((TypedByteArray) response.getBody()).getBytes());
        return job_log
    }

    String getJobLog(int jobId) {
        log.debug "fetching log by jobId ${jobId}"
        Response response = travisClient.jobLog(getAccessToken(), jobId)
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
        genericBuild.artifacts = ArtifactParser.getArtifactsFromLog(getLog(build), artifactRegexes)
        return genericBuild
    }

    GenericJobConfiguration getJobConfig(String inputRepoSlug) {
        String repoSlug = cleanRepoSlug(inputRepoSlug)
        Builds builds = travisClient.builds(getAccessToken(), repoSlug)
        return new GenericJobConfiguration(extractRepoFromRepoSlug(repoSlug), extractRepoFromRepoSlug(repoSlug), repoSlug ,true, getUrl(repoSlug),false, builds.builds.first().config?.parameterDefinitionList)
    }

    String getUrl(String repoSlug) {
        return "${baseUrl}/${repoSlug}"
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

    boolean hasRepo(String inputRepoSlug) {
        new SimpleHystrixCommand<Boolean>(
            groupKey, buildCommandKey("hasRepo"),
            {
                String repoSlug = cleanRepoSlug(inputRepoSlug)
                try {
                    getRepo(repoSlug)
                } catch (RetrofitError error) {
                    if (error.getResponse()) {
                        if (error.getResponse().status == 404) {
                            log.debug "repo not found ${repoSlug}"
                            return false
                        }
                    }
                    log.info "Error requesting repo ${repoSlug}: ${error.message}"
                    return true
                }
                return true
            }
        ).execute()
    }

    String branchedRepoSlug(String repoSlug, int buildNumber, Commit commit) {
        new SimpleHystrixCommand<String>(
            groupKey, buildCommandKey("branchedRepoSlug"),
            {
                Build build = getBuild(repoSlug, buildNumber)
                String branchedRepoSlug = "${repoSlug}/"
                if (build.pullRequest) {
                    branchedRepoSlug = "${branchedRepoSlug}pull_request_"
                }
                return "${branchedRepoSlug}${commit.branchNameWithTagHandling()}"
            },
            {
                return repoSlug
            }
        ).execute()
    }

    void syncRepos() {
        try {
            travisClient.usersSync(getAccessToken(), new EmptyObject())
        } catch (RetrofitError e) {
            log.error "synchronizing travis repositories for ${groupKey} failed with error: ${e.message}"
        }
    }

    protected static String cleanRepoSlug(String inputRepoSlug) {
        def parts = inputRepoSlug.tokenize('/')
        return "${parts[0]}/${parts[1]}"
    }

    protected static String branchFromRepoSlug(String inputRepoSlug) {
        String branch = extractBranchFromRepoSlug(inputRepoSlug) - ~/^pull_request_/
        return branch.equalsIgnoreCase('tags') ? '' : branch
    }

    protected static boolean branchIsTagsVirtualBranch(String inputRepoSlug) {
        return extractBranchFromRepoSlug(inputRepoSlug).equalsIgnoreCase("tags")
    }
    protected static boolean branchIsPullRequestVirtualBranch(String inputRepoSlug) {
        return extractBranchFromRepoSlug(inputRepoSlug).startsWith("pull_request_")
    }

    private static String extractBranchFromRepoSlug(String inputRepoSlug) {
        inputRepoSlug.tokenize('/').drop(2).join('/')
    }

    private static String extractRepoFromRepoSlug(String repoSlug) {
        return repoSlug.tokenize('/').get(1)
    }

    private void setAccessToken() {
        this.accessToken = travisClient.accessToken(gitHubAuth)
    }

    private String getAccessToken() {
        if (!accessToken) {
            setAccessToken()
        }
        return "token " + accessToken.accessToken
    }

    private Accounts getAccounts() {
        if (!accounts) {
            setAccounts()
        }
        return accounts
    }

    private void setAccounts() {
        this.accounts = travisClient.accounts(getAccessToken())
        log.debug "fetched " + accounts.accounts.size() + " accounts"
        accounts.accounts.each {
            log.debug "account: " + it.login
            log.debug "repos:" + it.reposCount
        }
    }

    private String buildCommandKey(String id) {
        return "${groupKey}-${id}"
    }
}

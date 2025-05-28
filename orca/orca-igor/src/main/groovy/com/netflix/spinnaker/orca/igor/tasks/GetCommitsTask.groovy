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

package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.model.Ami
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.igor.ScmService
import com.netflix.spinnaker.orca.kato.tasks.DiffTask
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class GetCommitsTask implements DiffTask {
  private static final int MAX_RETRIES = 3

  long backoffPeriod = 3000
  long timeout = TimeUnit.MINUTES.toMillis(5)
  // always set this higher than retries * backoffPeriod would take

  @Autowired
  CloudDriverService cloudDriverService

  @Autowired(required = false)
  ScmService scmService

  @Autowired(required = false)
  Front50Service front50Service

  @Override
  TaskResult execute(StageExecution stage) {
    def retriesRemaining = stage.context.getCommitsRetriesRemaining != null ? stage.context.getCommitsRetriesRemaining : MAX_RETRIES
    // is igor not configured or have we exceeded configured retries
    if (!scmService || retriesRemaining == 0) {
      log.info("igor is not configured or retries exceeded : scmService : ${scmService}, retries : ${retriesRemaining}")
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([commits: [], getCommitsRetriesRemaining: retriesRemaining]).build()
    }

    if (!front50Service) {
      log.warn("Front50 is not configured. Fix this by setting front50.enabled: true")
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([commits: [], getCommitsRetriesRemaining: retriesRemaining]).build()
    }

    Map repoInfo = [:]
    Map sourceInfo
    Map targetInfo

    try {
      // get app config to see if it has repo info
      repoInfo = getRepoInfo(stage.context.application)

      if (!repoInfo?.repoType || !repoInfo?.projectKey || !repoInfo?.repositorySlug) {
        log.info("not enough info to query igor for commits : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug}]")
        return TaskResult.SUCCEEDED
      }

      String region = stage.context?.source?.region ?: stage.context?.availabilityZones?.findResult { key, value -> key }
      String account = stage.context?.source?.account ?: stage.context?.account

      //figure out the old asg/ami/commit
      String ancestorAmi = getAncestorAmi(stage.context, region, account)
      // FIXME: multi-region deployments/canaries

      if (!ancestorAmi) {
        log.info "could not determine ancestor ami, this may be a new cluster with no ancestor asg"
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([commits: []]).build()
      }

      //figure out the new asg/ami/commit
      String targetAmi = getTargetAmi(stage.context, region)
      if (!targetAmi) {
        if (stage.execution.trigger instanceof PipelineTrigger) {
          def parentPipeline = stage.execution.trigger?.parentExecution
          while (!targetAmi && parentPipeline?.context) {
            targetAmi = getTargetAmi(parentPipeline.context, region)
            parentPipeline = parentPipeline.trigger instanceof PipelineTrigger ? parentPipeline.trigger?.parentExecution : null
          }
        }
      }

      //get commits from igor
      sourceInfo = resolveInfoFromAmi(ancestorAmi, account, region)
      targetInfo = resolveInfoFromAmi(targetAmi, account, region)

      //return results
      def outputs = [:]
      if (repoInfo?.repoType && repoInfo?.projectKey && repoInfo?.repositorySlug && sourceInfo?.commitHash && targetInfo?.commitHash) {
        outputs << [commits: getCommitsList(repoInfo.repoType, repoInfo.projectKey, repoInfo.repositorySlug, sourceInfo.commitHash, targetInfo.commitHash)]
      } else {
        log.warn("not enough info to query igor for commits : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceInfo} targetCommit: ${targetInfo}]")
      }

      if (outputs.commits && sourceInfo.build && targetInfo.build) {
        outputs << [buildInfo: [ancestor: sourceInfo.build, target: targetInfo.build]]
      }

      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == 404) {
        // just give up on 404
        return handle404(repoInfo, sourceInfo, targetInfo, e)
      } else { // retry on other status codes
        return retryOnException("SpinnakerHttpException (${e.message})", repoInfo, sourceInfo, targetInfo, retriesRemaining, e)
      }
    } catch (SpinnakerServerException e) {
      // give up on internal errors.  Note that this includes network errors
      return giveUpOnException(repoInfo, sourceInfo, targetInfo, e)
    } catch (Exception f) { // retry on everything else
      return retryOnException("unexpected exception", repoInfo, sourceInfo, targetInfo, retriesRemaining, f)
    } catch (Throwable g) {
      return retryOnException("unexpected throwable", repoInfo, sourceInfo, targetInfo, retriesRemaining, g)
    }
  }

  TaskResult giveUpOnException(Map repoInfo, Map sourceInfo, Map targetInfo, Exception e) {
    log.warn("internal error while talking to igor : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:$sourceInfo targetCommit: $targetInfo]", e)
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([commits: []]).build()
  }

  TaskResult handle404(Map repoInfo, Map sourceInfo, Map targetInfo, Exception e) {
    log.warn("got a 404 from igor for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceInfo} targetCommit: ${targetInfo}]", e)
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([commits: []]).build()
  }

  TaskResult retryOnException(String description, Map repoInfo, Map sourceInfo, Map targetInfo, int retriesRemaining, Throwable t) {
      log.warn("$description for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceInfo} targetCommit: ${targetInfo}], retrying", t)
      return TaskResult.builder(ExecutionStatus.RUNNING).context([getCommitsRetriesRemaining: retriesRemaining - 1]).build()
  }

  List getCommitsList(String repoType, String projectKey, String repositorySlug, String sourceCommit, String targetCommit) {
    List commitsList = []
    List commits = scmService.compareCommits(repoType, projectKey, repositorySlug, [to: sourceCommit, from: targetCommit, limit: 100])
    commits.each {
      // add commits to the task output
      commitsList << [displayId: it.displayId, id: it.id, authorDisplayName: it.authorDisplayName,
                      timestamp: it.timestamp, commitUrl: it.commitUrl, message: it.message]
    }
    return commitsList
  }

  Map resolveInfoFromAmi(String ami, String account, String region) {
    List<Ami> amiDetails = cloudDriverService.getByAmiId("aws", account, region, ami)
    def appVersion = amiDetails[0]?.tags?.appversion

    def buildInfo = [:]
    //Regex matches the last dot, some characters and then a slash
    if (appVersion && appVersion =~ /\.(?=[^.]*$)[a-z0-9]*\//) {
      def baseAppVersion = appVersion.substring(0, appVersion.indexOf('/'))
      buildInfo << [commitHash: baseAppVersion.substring(baseAppVersion.lastIndexOf('.') + 1)]
      buildInfo << [build: getBuildFromAppVersion(appVersion)]
    }
    return buildInfo
  }

  String getTargetAmi(Map context, region) {
    if (context.clusterPairs) { // canary cluster stage
      return context.clusterPairs?.find { clusterPair ->
        clusterPair?.canary?.availabilityZones?.findResult { key, value -> key == region }
      }?.canary?.amiName
    } else if (context.deploymentDetails) { // deploy asg stage
      return context.deploymentDetails.find { it.region == region }?.imageId
    } else if (context.amiName) { // copyLastAsg stage
      return context.amiName
    } else {
      return null
    }
  }

  String getAncestorAmi(Map context, String region, String account) {
    if (context.clusterPairs) { // separate cluster diff
      return context.clusterPairs?.find { clusterPair ->
        clusterPair?.baseline?.availabilityZones?.findResult { key, value -> key == region }
      }?.baseline?.amiName
    } else if (context.get("kato.tasks")) { // same cluster asg diff
      String ancestorAsg = context.get("kato.tasks")?.find { item ->
        item.find { key, value ->
          key == 'resultObjects'
        }
      }?.resultObjects?.ancestorServerGroupNameByRegion?.find {
        it.find { key, value ->
          key == region
        }
      }?.get(region)

      String sourceCluster
      if (!ancestorAsg) {
        return null
      } else if (ancestorAsg.lastIndexOf("-") > 0) {
        sourceCluster = ancestorAsg.substring(0, ancestorAsg.lastIndexOf("-"))
      } else {
        sourceCluster = ancestorAsg
      }

      ServerGroup sourceServerGroup = cloudDriverService.getServerGroupFromCluster(context.application,
        account, sourceCluster,
        ancestorAsg, region, "aws")
      return sourceServerGroup.launchConfig?.imageId
    }
  }

  Map getRepoInfo(String application) {
    Application app = Retrofit2SyncCall.execute(front50Service.get(application))
    return [repoType: app?.details()?.repoType, projectKey: app?.details()?.repoProjectKey, repositorySlug: app?.details()?.repoSlug]
  }

  String getBuildFromAppVersion(String appVersion) {
    int slash = appVersion?.lastIndexOf('/') ?: -1
    return slash >= 0 ? appVersion?.substring(slash + 1) : appVersion
  }
}

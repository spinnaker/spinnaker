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

import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.kato.tasks.DiffTask
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
class GetCommitsTask implements DiffTask {
  private static final int MAX_RETRIES = 3

  long backoffPeriod = 3000
  long timeout = TimeUnit.MINUTES.toMillis(5)
  // always set this higher than retries * backoffPeriod would take

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Autowired(required = false)
  BuildService buildService

  @Autowired(required = false)
  Front50Service front50Service

  @Override
  TaskResult execute(Stage stage) {
    def retriesRemaining = stage.context.getCommitsRetriesRemaining != null ? stage.context.getCommitsRetriesRemaining : MAX_RETRIES
    // is igor not configured or have we exceeded configured retries
    if (!buildService || retriesRemaining == 0) {
      log.info("igor is not configured or retries exceeded : buildService : ${buildService}, retries : ${retriesRemaining}")
      return new TaskResult(ExecutionStatus.SUCCEEDED, [commits: [], getCommitsRetriesRemaining: retriesRemaining])
    }

    if (!front50Service) {
      log.warn("Front50 is not configured. Fix this by setting front50.enabled: true")
      return new TaskResult(ExecutionStatus.SUCCEEDED, [commits: [], getCommitsRetriesRemaining: retriesRemaining])
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
        return new TaskResult(ExecutionStatus.SUCCEEDED, [commits: []])
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

      return new TaskResult(ExecutionStatus.SUCCEEDED, outputs)
    } catch (RetrofitError e) {
      if (e.kind == RetrofitError.Kind.UNEXPECTED) {
        // give up on internal errors
        log.error("internal error while talking to igor : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:$sourceInfo targetCommit: $targetInfo]")
        return new TaskResult(ExecutionStatus.SUCCEEDED, [commits: []])
      } else if (e.response?.status == 404) {
        // just give up on 404
        log.error("got a 404 from igor for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceInfo} targetCommit: ${targetInfo}]")
        return new TaskResult(ExecutionStatus.SUCCEEDED, [commits: []])
      } else { // retry on other status codes
        log.error("retrofit error (${e.message}) for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceInfo} targetCommit: ${targetInfo}], retrying")
        return new TaskResult(ExecutionStatus.RUNNING, [getCommitsRetriesRemaining: retriesRemaining - 1])
      }
    } catch (Exception f) { // retry on everything else
      log.error("unexpected exception for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceInfo} targetCommit: ${targetInfo}], retrying", f)
      return new TaskResult(ExecutionStatus.RUNNING, [getCommitsRetriesRemaining: retriesRemaining - 1])
    } catch (Throwable g) {
      log.error("unexpected throwable for : [repoType: ${repoInfo?.repoType} projectKey:${repoInfo?.projectKey} repositorySlug:${repoInfo?.repositorySlug} sourceCommit:${sourceInfo} targetCommit: ${targetInfo}], retrying", g)
      return new TaskResult(ExecutionStatus.RUNNING, [getCommitsRetriesRemaining: retriesRemaining - 1])
    }
  }

  List getCommitsList(String repoType, String projectKey, String repositorySlug, String sourceCommit, String targetCommit) {
    List commitsList = []
    List commits = buildService.compareCommits(repoType, projectKey, repositorySlug, [to: sourceCommit, from: targetCommit, limit: 100])
    commits.each {
      // add commits to the task output
      commitsList << [displayId: it.displayId, id: it.id, authorDisplayName: it.authorDisplayName,
                      timestamp: it.timestamp, commitUrl: it.commitUrl, message: it.message]
    }
    return commitsList
  }

  Map resolveInfoFromAmi(String ami, String account, String region) {
    List<Map> amiDetails = oortService.getByAmiId("aws", account, region, ami)
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

      TypeReference<Map> jsonMapType = new TypeReference<Map>() {}
      Map sourceServerGroup = objectMapper.readValue(oortService.getServerGroupFromCluster(context.application,
        account, sourceCluster,
        ancestorAsg, region, "aws").body.in(), jsonMapType)
      return sourceServerGroup.launchConfig.imageId
    }
  }

  Map getRepoInfo(String application) {
    Application app = front50Service.get(application)
    return [repoType: app?.details()?.repoType, projectKey: app?.details()?.repoProjectKey, repositorySlug: app?.details()?.repoSlug]
  }

  String getBuildFromAppVersion(String appVersion) {
    int slash = appVersion?.lastIndexOf('/') ?: -1
    return slash >= 0 ? appVersion?.substring(slash + 1) : appVersion
  }
}

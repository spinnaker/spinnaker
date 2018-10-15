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

package com.netflix.spinnaker.orca.kato.tasks

import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.libdiffs.ComparableLooseVersion
import com.netflix.spinnaker.orca.libdiffs.Library
import com.netflix.spinnaker.orca.libdiffs.LibraryDiffTool
import com.netflix.spinnaker.orca.libdiffs.LibraryDiffs
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.squareup.okhttp.OkHttpClient
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.client.OkClient

@Slf4j
@Component
@Import(RetrofitConfiguration)
@ConditionalOnBean(ComparableLooseVersion)
@ConditionalOnProperty(value = 'jarDiffs.enabled', matchIfMissing = false)
class JarDiffsTask implements DiffTask {

  private static final int MAX_RETRIES = 10

  long backoffPeriod = 10000
  long timeout = TimeUnit.MINUTES.toMillis(5) // always set this higher than retries * backoffPeriod would take

  @Autowired
  ComparableLooseVersion comparableLooseVersion

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  OortService oortService

  @Autowired
  OortHelper oortHelper

  int platformPort = 8077

  @Override
  public TaskResult execute(Stage stage) {
    def retriesRemaining = stage.context.jarDiffsRetriesRemaining != null ? stage.context.jarDiffsRetriesRemaining : MAX_RETRIES
    if (retriesRemaining <= 0) {
      log.info("retries exceeded")
      return new TaskResult(ExecutionStatus.SUCCEEDED, [jarDiffsRetriesRemaining: retriesRemaining])
    }

    try {
      String region = stage.context?.source?.region ?: stage.context?.availabilityZones?.findResult { key, value -> key }
      // figure out source + target asgs
      String targetAsg = getTargetAsg(stage.context, region)
      String sourceAsg = getSourceAsg(stage.context, region)

      def targetInstances = [:]
      def sourceInstances = [:]
      try {
        // get healthy instances from each
        targetInstances = oortHelper.getInstancesForCluster(stage.context, targetAsg, false, false)
        sourceInstances = oortHelper.getInstancesForCluster(stage.context, sourceAsg, false, false)
      } catch (Exception e) {
        log.error("Unable to fetch instances (targetAsg: ${targetAsg}, sourceAsg: ${sourceAsg}), reason: ${e.message}")
      }

      if (!targetInstances || !sourceInstances) {
        log.debug("No instances found (targetAsg: ${targetAsg}, sourceAsg: ${sourceAsg})")
        return new TaskResult(ExecutionStatus.SUCCEEDED)
      }

      // get jar json info
      List targetJarList = getJarList(targetInstances)
      List sourceJarList = getJarList(sourceInstances)

      // diff
      LibraryDiffTool libraryDiffTool = new LibraryDiffTool(comparableLooseVersion, false)
      LibraryDiffs jarDiffs = libraryDiffTool.calculateLibraryDiffs(sourceJarList, targetJarList)

      // add the diffs to the context
      return new TaskResult(ExecutionStatus.SUCCEEDED, [jarDiffs: jarDiffs])
    } catch (Exception e) {
      // return success so we don't break pipelines
      log.error("error while fetching jar diffs, retrying", e)
      return new TaskResult(ExecutionStatus.RUNNING, [jarDiffsRetriesRemaining: --retriesRemaining])
    }
  }

  InstanceService createInstanceService(String address) {
    RestAdapter restAdapter = new RestAdapter.Builder()
      .setEndpoint(address)
      .setClient(new OkClient(new OkHttpClient(retryOnConnectionFailure: false)))
      .build()
    return restAdapter.create(InstanceService.class)
  }

  List getJarList(Map instances) {
    List jarList = []
    Map jarMap = [:]

    int numberOfInstancesChecked = 0;
    instances.find { String key, Map valueMap ->
      if (numberOfInstancesChecked++ >= 5) {
        log.info("Unable to check jar list after 5 attempts, giving up!")
        return true
      }

      String hostName = valueMap.privateIpAddress ?: valueMap.hostName
      log.debug("attempting to get a jar list from : ${key} (${hostName}:${platformPort})")
      def instanceService = createInstanceService("http://${hostName}:${platformPort}")
      try {
        def instanceResponse = instanceService.getJars()
        jarMap = objectMapper.readValue(instanceResponse.body.in().text, Map)
        return true
      } catch(Exception e) {
        log.debug("could not get a jar list from : ${key} (${hostName}:${platformPort}) - ${e.message}")
        // swallow it so we can try the next instance
        return false
      }
    }

    jarMap.jars.each { jar ->
      jarList << getLibraryFromJson(jar)
    }
    return jarList
  }

  Library getLibraryFromJson(def json) {
    String jarName = json.name.substring(json.name.lastIndexOf("/") + 1, json.name.indexOf(".jar"))
    Matcher versionInName = jarName =~ /([a-zA-Z0-9\._-]+)-([0-9\.]+).*/
    Matcher noVersionInName = jarName =~ /([a-zA-Z0-9\._-]+).*/

    String name, version
    if (versionInName.matches()) {
      name = jarName.substring(0, versionInName.end(1))
      version = jarName.substring(versionInName.end(1) + 1)
    } else if (noVersionInName.matches()) {
      name = jarName.substring(0, noVersionInName.end(1))
      version = json.implementationVersion ?: "0.0.0"
    } else {
      name = jarName
      version = json.implementationVersion ?: "0.0.0"
    }

    Map lib = [:]
    lib << [filePath : json.name.replaceAll("\"", "")]
    lib << [name : name.replaceAll("\"", "")]
    lib << [version : version?.replaceAll("\"", "")]
    if (json.implementationTitle) {
      lib << [org : json.implementationTitle.contains("#") ?
        json.implementationTitle.substring(0, json.implementationTitle.indexOf("#")).replaceAll("\"", "") :
        json.implementationTitle.replaceAll("\"", "")]
    }
    if (json.buildDate) {
      lib << [buildDate : json.buildDate]
    }
    if (json.status) {
      lib << [status : json.status]
    }
    return new Library(lib.filePath, lib.name, lib.version, lib.org, lib.status)
  }

  String getTargetAsg(Map context, String region) {
    if(context.clusterPairs) {
      // todo
    } else if (context."kato.tasks") { // deploy asg stage
      return context.get("kato.tasks")?.find { item ->
        item.find { key, value ->
          key == 'resultObjects'
        }
      }?.resultObjects?.find { another ->
        another.find { key, value ->
          key == "serverGroupNameByRegion"
        }
      }?.serverGroupNameByRegion?.get(region)
    } else {
      return null
    }
  }

  String getSourceAsg(Map context, String region) {
    if(context.clusterPairs) {
      //todo
    }else if (context."kato.tasks") { // deploy asg stage
      return context.get("kato.tasks")?.find { item ->
        item.find { key, value ->
          key == 'resultObjects'
        }
      }?.resultObjects?.find { another ->
        another.find { key, value ->
          key == "ancestorServerGroupNameByRegion"
        }
      }?.ancestorServerGroupNameByRegion?.get(region)
    } else {
      return null
    }
  }
}

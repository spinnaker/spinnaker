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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.libdiffs.Library
import com.netflix.spinnaker.orca.libdiffs.LibraryDiff
import com.netflix.spinnaker.orca.oort.InstanceService
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.oort.util.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import retrofit.RestAdapter

import java.util.regex.Matcher

@Slf4j
@Component
@ConditionalOnProperty(value = 'jarDiffs.enabled', matchIfMissing = false)
class JarDiffsTask implements Task {
  @Autowired
  ObjectMapper objectMapper

  @Autowired
  OortService oortService

  @Autowired
  OortHelper oortHelper

  int platformPort = 8077

  @Override public TaskResult execute(Stage stage) {
    try {
      String region = stage.context?.source?.region ?: stage.context?.availabilityZones?.findResult { key, value -> key }
      // figure out source + target asgs
      String targetAsg = getTargetAsg(stage.context, region)
      String sourceAsg = getSourceAsg(stage.context, region)

      // get healthy instances from each
      Map targetInstances = oortHelper.getInstancesForCluster(stage.context, targetAsg, false, false)
      Map sourceInstances = oortHelper.getInstancesForCluster(stage.context, sourceAsg, false, false)

      // get jar json info
      List targetJarList = getJarList(targetInstances)
      List sourceJarList = getJarList(sourceInstances)

      // diff
      LibraryDiff libDiff = new LibraryDiff(sourceJarList, targetJarList)
      Map jarDiffs = libDiff.diffJars()

      // add the diffs to the context
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [jarDiffs: jarDiffs])
    } catch (Exception e) {
      println "exception : ${e.dump()}"
      // return success so we don't break pipelines
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [jarDiffs: [:]])
    }
  }

  InstanceService createInstanceService(String address) {
    RestAdapter restAdapter = new RestAdapter.Builder()
      .setEndpoint(address)
      .build()
    return restAdapter.create(InstanceService.class)
  }

  List getJarList(Map instances) {
    List jarList = []
    Map jarMap = [:]
    instances.each { String key, Map valueMap ->
      String hostName = valueMap.hostName
      def instanceService = createInstanceService("http://${hostName}:${platformPort}")
      try {
        def instanceResponse = instanceService.getJars()
        jarMap = objectMapper.readValue(instanceResponse.body.in().text, Map)
        return true
      } catch(Exception e) {
        // swallow it so we can try the next instance
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

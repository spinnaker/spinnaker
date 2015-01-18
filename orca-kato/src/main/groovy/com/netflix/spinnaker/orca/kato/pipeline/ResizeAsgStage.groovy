/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.kato.tasks.*
import com.netflix.spinnaker.orca.kato.tasks.ResizeAsgTask
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ResizeAsgStage extends LinearStage {
  static final String MAYO_CONFIG_TYPE = "resizeAsg"

  @Autowired
  OortService oort

  @Autowired
  ObjectMapper mapper

  ResizeAsgStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps(Stage stage) {
    configureTargets(stage)

    def step1 = buildStep("resizeAsg", ResizeAsgTask)
    def step2 = buildStep("monitorAsg", MonitorKatoTask)
    def step3 = buildStep("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    def step4 = buildStep("waitForCapacityMatch", WaitForCapacityMatchTask)
    [step1, step2, step3, step4]
  }

  @CompileDynamic
  private void configureTargets(Stage stage) {
    def optionalConfig = stage.mapTo(OptionalConfiguration)
    def requiredConfig = stage.mapTo(RequiredConfiguration)

    Map<String, Map<String, Object>> descriptions = [:]

    if (optionalConfig.cluster && !optionalConfig.target) {
      throw new RuntimeException("Stage configuration failed: cluster was provided but no target specified")
    }
    if (!optionalConfig.cluster && !optionalConfig.asgName) {
      throw new RuntimeException("No target was able to be derived!")
    }

    def names = Names.parseName(optionalConfig.cluster ?: optionalConfig.asgName)
    def existingAsgs = getExistingAsgs(names.app, requiredConfig.credentials, names.cluster,
      optionalConfig.providerType)

    if (!existingAsgs) {
      throw new RuntimeException("Could not find cluster!")
    }
    Map<String, List<Map>> asgsByRegion = (Map<String, List<Map>>)existingAsgs.groupBy { Map asg -> asg.region }
    for (Map.Entry<String, List<Map>> entry in asgsByRegion) {
      def region = entry.key
      if (!requiredConfig.regions.contains(region)) {
        continue
      }

      def sortedAsgs = entry.value.sort { a, b -> b.name <=> a.name }
      def description = new HashMap(stage.context)

      def asg
      if (optionalConfig.cluster && optionalConfig.target == ResizeTarget.current_asg) {
        asg = sortedAsgs.get(0)
      } else if (optionalConfig.cluster && sortedAsgs.size() >= 2 && optionalConfig.target == ResizeTarget.ancestor_asg) {
        asg = sortedAsgs.get(1)
      } else {
        asg = sortedAsgs.find { it.name == optionalConfig.asgName }
      }
      if (descriptions.containsKey(asg.name)) {
        descriptions[asg.name].regions.add(region)
        continue
      }
      description.asgName = asg.name
      description.regions = [asg.region]

      def currentMin = Integer.parseInt(asg.asg.minSize.toString())
      def currentDesired = Integer.parseInt(asg.asg.desiredCapacity.toString())
      def currentMax = Integer.parseInt(asg.asg.maxSize.toString())

      int newMin, newDesired, newMax
      if (optionalConfig.scalePct) {
        def factor = Math.ceil(optionalConfig.scalePct / 100)
        def minDiff = currentMin * factor
        def desiredDiff = currentDesired * factor
        def maxDiff = currentMax * factor
        newMin = currentMin + minDiff
        newDesired = currentDesired + desiredDiff
        newMax = currentMax + maxDiff

        if (optionalConfig.action == ResizeAction.scale_down) {
          newMin = currentMin - minDiff
          newDesired = currentDesired - desiredDiff
          newMax = currentMax - maxDiff
        }
      } else if (optionalConfig.scaleNum) {
        newMin = currentMin + optionalConfig.scaleNum
        newDesired = currentDesired + optionalConfig.scaleNum
        newMax = currentMax + optionalConfig.scaleNum

        if (optionalConfig.action == ResizeAction.scale_down) {
          newMin = currentMin - optionalConfig.scaleNum
          newDesired = currentDesired - optionalConfig.scaleNum
          newMax = currentMax - optionalConfig.scaleNum
        }
      }

      if (newMin && newDesired && newMax) {
        description.capacity = [min: newMin, desired: newDesired, max: newMax]
      }


      descriptions[asg.name] = description
    }

    def descriptionList = descriptions.values()
    def first = descriptionList[0]
    descriptionList.remove(first)
    stage.context.putAll(first)

    if (descriptionList.size()) {
      for (description in descriptionList) {
        injectAfter("resizeAsg", this, description)
      }
    }
  }

  private List<Map> getExistingAsgs(String app, String account, String cluster, String providerType) {
    try {
      def response = oort.getCluster(app, account, cluster, providerType)
      def json = response.body.in().text
      def map = mapper.readValue(json, Map)
      map.serverGroups as List<Map>
    } catch (e) {
      null
    }
  }

  enum ResizeAction {
    scale_up, scale_down
  }

  enum ResizeTarget {
    current_asg, ancestor_asg
  }

  static class OptionalConfiguration {
    ResizeAction action
    ResizeTarget target
    Integer scalePct
    Integer scaleNum
    String asgName
    String cluster
    String providerType = "aws"
  }

  static class RequiredConfiguration {
    String credentials
    List<String> regions
  }
}

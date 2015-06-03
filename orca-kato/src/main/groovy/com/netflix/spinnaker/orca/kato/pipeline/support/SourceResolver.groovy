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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.kato.pipeline.strategy.DeployStrategyStage
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class SourceResolver {

  @Autowired OortService oortService
  @Autowired ObjectMapper mapper


  StageData.Source getSource(Stage stage) {
    def stageData = stage.mapTo(StageData)
    if (stageData.source) {
      // has an existing source, return it
      return stageData.source
    }

    def existingAsgs = getExistingAsgs(
      stageData.application, stageData.account, stageData.cluster, stageData.providerType
    )

    if (!existingAsgs || !stageData.availabilityZones) {
      return null
    }

    def targetRegion = stageData.availabilityZones.keySet()[0]

    def regionalAsgs = existingAsgs.findAll { it.region == targetRegion }
    def latestAsg = regionalAsgs ? regionalAsgs.last() : null

    return latestAsg ? new StageData.Source(
      account: stageData.account, region: latestAsg["region"] as String, asgName: latestAsg["name"] as String
    ) : null
  }

  List<Map> getExistingAsgs(String app, String account, String cluster, String providerType) {
    try {
      def response = oortService.getCluster(app, account, cluster, providerType)
      def json = response.body.in().text
      def map = mapper.readValue(json, Map)
      sortAsgs(map.serverGroups as List<Map>)
    } catch (e) {
      null
    }
  }

  protected List<Map> sortAsgs(List<Map> asgs) {
    return asgs?.sort(true, ServerGroupNameComparator.forServerGroups())
  }

  static class ServerGroupNameComparator {

    static Comparator<String> forNames() {
      return new NameComparator()
    }

    static Comparator<Map> forServerGroups() {
      return new ServerGroupComparator()
    }

    static class NameComparator implements Comparator<String> {
      @Override
      int compare(String a, String b) {
        // cases where there is no version
        if (a.lastIndexOf("-v") == -1 && Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2)) > 900) {
          1
        } else if (a.lastIndexOf("-v") == -1 && Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2)) < 900) {
          -1
        } else if (b.lastIndexOf("-v") == -1 && Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2)) < 900) {
          1
        } else if (b.lastIndexOf("-v") == -1 && Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2)) > 900) {
          -1
          // cases where versions cross 999
        } else if (Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2)) < 900 && Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2)) > 900) {
          1
        } else if (Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2)) > 900 && Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2)) < 900) {
          -1
        } else { // normal case
          int aNum = Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2))
          int bNum = Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2))
          aNum.equals(bNum) ? 0 : Math.abs(aNum) < Math.abs(bNum) ? -1 : 1
        }
      }
    }

    static class ServerGroupComparator implements Comparator<Map> {
      private final NameComparator comparator = new NameComparator()

      @Override
      int compare(Map o1, Map o2) {
        comparator.compare(o1.name as String, o2.name as String)
      }
    }
  }

}

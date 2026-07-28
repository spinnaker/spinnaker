/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.front50.multiplepipelines;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ObjectUtils;

public class UtilityHelper {

  public Apps getApps(RunMultiplePipelinesContext context, ObjectMapper mapper) {
    BundleWeb bundleWeb = mapper.convertValue(context.getYamlConfig().get(0), BundleWeb.class);

    return mapper.convertValue(bundleWeb.getBundleWeb(), Apps.class);
  }

  public MutableGraph<App> getGraphOfApps(Map<String, App> mapOfApps, List<App> initialExecutions) {
    MutableGraph<App> graph = GraphBuilder.directed().allowsSelfLoops(false).build();
    // set yamlIdentifier on the App Object before adding as node of graph
    mapOfApps.replaceAll(
        (k, v) -> {
          v.setYamlIdentifier(k);
          return v;
        });

    mapOfApps.forEach(
        (k, v) -> {
          if (ObjectUtils.isNotEmpty(v.getDependsOn())) {
            v.getDependsOn()
                .forEach(
                    appName -> {
                      graph.putEdge(mapOfApps.get(appName), mapOfApps.get(k));
                    });
          } else {
            // add nodes of not connected components
            graph.addNode(mapOfApps.get(k));
            initialExecutions.add(mapOfApps.get(k));
          }
        });
    return graph;
  }

  /**
   * Modifies orderOfExecutions using recursion
   *
   * <p>adds new levels searching through the graph looping on the successors of that particular
   * node
   *
   * <p>before adding checks that his predecessors are already added and himself has not been added
   */
  public void addLevels(
      List<List<App>> orderOfExecutions,
      MutableGraph<App> graph2,
      List<App> allAppsInWholeQueue,
      int i) {
    List<List<App>> listListOfApps = new LinkedList<>();
    for (App app : orderOfExecutions.get(i)) {
      if (!allAppsInWholeQueue.contains(app)) {
        allAppsInWholeQueue.add(app);
      }
    }
    List<App> newLevel = new LinkedList<>();
    for (App appI : orderOfExecutions.get(i)) {
      graph2
          .successors(appI)
          .forEach(
              app -> {
                if (allAppsInWholeQueue.containsAll(graph2.predecessors(app))) {
                  if (!allAppsInWholeQueue.contains(app)) {
                    if (!newLevel.contains(app)) {
                      newLevel.add(app);
                    }
                  }
                }
              });
    }
    if (ObjectUtils.isNotEmpty(newLevel)) {
      listListOfApps.add(newLevel);
    }
    if (ObjectUtils.isNotEmpty(listListOfApps)) {
      orderOfExecutions.addAll(listListOfApps);
      addLevels(orderOfExecutions, graph2, allAppsInWholeQueue, ++i);
    }
  }
}

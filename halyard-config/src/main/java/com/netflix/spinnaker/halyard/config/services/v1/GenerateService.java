/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.AtomicFileWriter;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeReference;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.ComponentName;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.ComponentProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GenerateService {
  @Autowired
  ComponentProfile componentProfile;

  @Autowired
  LookupService lookupService;

  @Autowired
  String spinnakerOutputPath;

  @Autowired
  Yaml yamlParser;

  @Autowired
  ObjectMapper objectMapper;

  public void generateConfig(NodeReference nodeReference) {
    NodeFilter filter = NodeFilter.makeAcceptAllFilter()
        .refineWithReference(nodeReference);

    List<Node> matching = lookupService.getMatchingNodesOfType(filter, Node.class);

    Map<ComponentName, List<Node>> configInjectMap = new HashMap<>();
    for (Node node : matching) {
      for (ComponentName componentName : node.registeredSpinnakerComponents()) {
        log.trace("Registering " + node.getNodeName() + " with component " + componentName);

        List<Node> injectable = configInjectMap.getOrDefault(componentName, new ArrayList<>());
        injectable.add(node);
        configInjectMap.put(componentName, injectable);
      }
    }

    for (ComponentName componentName : ComponentName.values()) {
      FileSystem defaultFileSystem = FileSystems.getDefault();
      AtomicFileWriter writer = null;
      try {
        Path path = defaultFileSystem.getPath(spinnakerOutputPath, componentName.getProfile());
        log.info("Writing profile to  " + path);

        writer = new AtomicFileWriter(path);

        for (Node config : configInjectMap.getOrDefault(componentName, new ArrayList<>())) {
          writer.write(yamlParser.dump(objectMapper.convertValue(config, Map.class)));
        }

        writer.write("\n### Fetched config begins here\n\n");

        String componentYaml = componentProfile.getProfile(componentName);
        writer.write(componentYaml);

        writer.commit();
      }
      catch (IOException ioe) {
        ioe.printStackTrace();
        throw new HalconfigException(
            new ProblemBuilder(Problem.Severity.FATAL,
                "Failed to write config for component " + componentName + ": " + ioe.getMessage()).build()
        );
      } finally {
        if (writer != null) {
          writer.close();
        }
      }
    }
  }
}

/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.halyard.config.spinnaker.v1;

import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.profileRegistry.ComponentProfileRegistryService;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.profileRegistry.StoredObjectMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import retrofit.RetrofitError;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ComponentProfile {
  @Autowired
  HalconfigParser parser;

  @Autowired
  Yaml yamlParser;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  String spinconfigBucket;

  @Autowired
  ComponentProfileRegistryService componentProfileRegistryService;

  private InputStream getContents(String objectName) throws IOException {
    ComponentProfileRegistryService service = componentProfileRegistryService;

    StoredObjectMetadata metadata = service.getMetadata(spinconfigBucket, objectName);

    return service.getContents(spinconfigBucket, objectName, metadata.getGeneration(), "media").getBody().in();
  }

  public String getProfile(ComponentName componentName) {
    Halconfig currentConfig = parser.getConfig(true);

    if (currentConfig.getSpinnakerVersion() == null || currentConfig.getSpinnakerVersion().isEmpty()) {
      throw new IllegalConfigException(
          new ProblemBuilder(Problem.Severity.FATAL,
              "In order to load a Spinnaker Component's profile, you must specify a version of Spinnaker in your halconfig")
              .build()
      );
    }

    try {
      String bomName = "bom/" + currentConfig.getSpinnakerVersion() + ".yml";

      BillOfMaterials bom = objectMapper.convertValue(
          yamlParser.load(getContents(bomName)),
          BillOfMaterials.class
      );

      String componentVersion = bom.getServices().getComponentVersion(componentName);

      String componentObjectName = componentName.getId() + "/" + componentVersion + "/" + componentName.getProfile();

      return IOUtils.toString(getContents(componentObjectName));
    } catch (RetrofitError | IOException e) {
      throw new HalconfigException(
          new ProblemBuilder(Problem.Severity.FATAL,
              "Unable to retrieve a profile for " + componentName.getId() + ": " + e.getMessage())
              .build()
      );
    }
  }
}

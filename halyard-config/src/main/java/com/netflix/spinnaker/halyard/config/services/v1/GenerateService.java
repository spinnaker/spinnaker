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

import com.netflix.spinnaker.halyard.config.config.v1.AtomicFileWriter;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.component.SpinnakerComponent;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GenerateService {
  @Autowired
  String spinnakerOutputPath;

  @Autowired(required = false)
  List<SpinnakerComponent> spinnakerComponents = new ArrayList<>();

  public void generateConfig(NodeFilter nodeFilter) {
    for (SpinnakerComponent component : spinnakerComponents) {
      FileSystem defaultFileSystem = FileSystems.getDefault();
      AtomicFileWriter writer = null;
      Path path = defaultFileSystem.getPath(spinnakerOutputPath, component.getConfigFileName());

      log.info("Writing profile to  " + path);

      try {
        writer = new AtomicFileWriter(path);
        writer.write(component.getFullConfig(nodeFilter));
        writer.commit();
      } catch (IOException ioe) {
        ioe.printStackTrace();
        throw new HalconfigException(
            new ProblemBuilder(Problem.Severity.FATAL,
                "Failed to write config for component " + component.getComponentName() + ": " + ioe
                    .getMessage()).build()
        );
      } finally {
        if (writer != null) {
          writer.close();
        }
      }
    }
  }
}

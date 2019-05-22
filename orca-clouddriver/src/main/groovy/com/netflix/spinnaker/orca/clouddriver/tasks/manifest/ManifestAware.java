/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public interface ManifestAware {
  default Map<String, List<String>> manifestNamesByNamespace(Stage stage) {
    Map<String, List<String>> result =
        (Map<String, List<String>>)
            stage
                .getContext()
                .get(
                    PromoteManifestKatoOutputsTask.outputKey(
                        PromoteManifestKatoOutputsTask.MANIFESTS_BY_NAMESPACE_KEY));
    if (result != null) {
      return result;
    }

    result = new HashMap<>();
    String name = (String) stage.getContext().get("manifest.name");
    String location = (String) stage.getContext().get("manifest.location");
    if (name != null && location != null) {
      result.put(location, Collections.singletonList(name));
    } else {
      Logger.getLogger(this.getClass().getName())
          .warning("No manifests found in stage " + stage.getId());
    }

    return result;
  }

  default Map<String, List<String>> manifestsToRefresh(Stage stage) {
    Map<String, List<String>> result =
        (Map<String, List<String>>)
            stage
                .getContext()
                .get(PromoteManifestKatoOutputsTask.MANIFESTS_BY_NAMESPACE_TO_REFRESH_KEY);
    if (result != null
        && (boolean)
            stage
                .getContext()
                .get(
                    PromoteManifestKatoOutputsTask
                        .SHOULD_REFRESH_MANIFESTS_BY_NAMESPACE_TO_REFRESH_KEY)) {
      return result;
    }
    return manifestNamesByNamespace(stage);
  }
}

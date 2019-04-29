/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.kork.secrets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SecretEngineRegistry {

  @Getter private Map<String, SecretEngine> registeredEngines = new HashMap<>();

  @Getter @Autowired private List<SecretEngine> secretEngineList;

  @PostConstruct
  public void init() {
    for (SecretEngine secretEngine : secretEngineList) {
      registeredEngines.put(secretEngine.identifier(), secretEngine);
    }
  }

  public SecretEngine getEngine(String key) {
    return registeredEngines.get(key);
  }
}

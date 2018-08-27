/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.consul;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.TemplateBackedProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Component
@Data
public class ConsulClientProfileFactory extends TemplateBackedProfileFactory {
  private String template = String.join("\n",
      "{",
      "    \"server\": false,",
      "    \"datacenter\": \"spinnaker\",",
      "    \"data_dir\": \"/var/consul\",",
      "    \"log_level\": \"INFO\",",
      "    \"enable_syslog\": true,",
      "    \"ports\": {",
      "        \"dns\": 53,",
      "        \"{%scheme%}\": {%port%}",
      "    },",
      "    \"recursors\": [ \"169.254.169.254\" ]",
      "}"
  );

  @Override
  protected Map<String, Object> getBindings(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    Map<String, Object> bindings = new HashMap<>();
    ServiceSettings consul = endpoints.getServiceSettings(Type.CONSUL_CLIENT);
    bindings.put("scheme", consul.getScheme());
    bindings.put("port", consul.getPort() + "");
    return bindings;
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.CONSUL;
  }

  @Override
  protected String commentPrefix() {
    return "// ";
  }

  @Override
  protected boolean showEditWarning() {
    return false;
  }
}

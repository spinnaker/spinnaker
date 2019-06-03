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
 */

package com.netflix.spinnaker.halyard.deploy.spin.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpinService {

  @Autowired HalconfigDirectoryStructure halconfigDirectoryStructure;

  public RemoteAction install() {
    RemoteAction result = new RemoteAction();
    String script =
        "#!/bin/bash\n"
            + "curl -LO https://storage.googleapis.com/spinnaker-artifacts/spin/$(curl -s https://storage.googleapis.com/spinnaker-artifacts/spin/latest)/linux/amd64/spin\n"
            + "chmod +x spin\n"
            + "sudo mv spin /usr/local/bin/spin";
    result.setScript(script);
    result.setScriptDescription("The generated script will install the latest version of spin CLI");
    result.setAutoRun(true);
    result.commitScript(halconfigDirectoryStructure.getSpinInstallScriptPath());
    return result;
  }
}

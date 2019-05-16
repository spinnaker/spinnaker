/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.providers.util

import com.netflix.spinnaker.rosco.config.RoscoPackerConfigurationProperties
import org.springframework.beans.factory.annotation.Autowired

class LocalJobFriendlyPackerCommandFactory implements PackerCommandFactory {

  @Autowired RoscoPackerConfigurationProperties roscoPackerConfigurationProperties

  @Override
  List<String> buildPackerCommand(String baseCommand,
                                  Map<String, String> parameterMap,
                                  String absoluteVarFilePath,
                                  String absoluteTemplateFilePath) {
    def packerCommand = [baseCommand, "packer", "build", "-color=false"]
    if (roscoPackerConfigurationProperties.timestamp) {
      packerCommand.add("-timestamp-ui")
    }

    packerCommand.addAll(roscoPackerConfigurationProperties.additionalParameters)

    parameterMap.each { key, value ->
      if (key && value) {
        def keyValuePair = "$key=${value instanceof String ? value.trim() : value}"

        packerCommand << "-var"
        packerCommand << keyValuePair.toString()
      }
    }

    if (absoluteVarFilePath) {
      packerCommand << "-var-file=$absoluteVarFilePath".toString()
    }

    packerCommand << absoluteTemplateFilePath
    packerCommand.removeAll([null, ""])

    packerCommand
  }

}

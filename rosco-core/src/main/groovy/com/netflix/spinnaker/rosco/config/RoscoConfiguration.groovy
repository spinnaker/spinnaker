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

package com.netflix.spinnaker.rosco.config

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.registry.DefaultCloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.util.DefaultImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.DefaultPackerCommandFactory
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@CompileStatic
class RoscoConfiguration {

  @Bean
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry(@Value('${defaultCloudProviderType:aws}') BakeRequest.CloudProviderType defaultCloudProviderType) {
    return new DefaultCloudProviderBakeHandlerRegistry(defaultCloudProviderType)
  }

  @Bean
  ImageNameFactory imageNameFactory() {
    return new DefaultImageNameFactory()
  }

  @Bean
  PackerCommandFactory packerCommandFactory() {
    return new DefaultPackerCommandFactory()
  }

  @Bean
  @ConfigurationProperties('executionStatusToBakeStates')
  ExecutionStatusToBakeStateMap executionStatusToBakeStateMap() {
    new ExecutionStatusToBakeStateMap()
  }

  static class ExecutionStatusToBakeStateMap {
    List<ExecutionStatusToBakeState> associations

    public BakeStatus.State convertExecutionStatusToBakeState(String executionStatus) {
      associations.find {
        it.executionStatus == executionStatus
      }?.bakeState
    }
  }

  static class ExecutionStatusToBakeState {
    String executionStatus
    BakeStatus.State bakeState
  }

  @Bean
  @ConfigurationProperties('executionStatusToBakeResults')
  ExecutionStatusToBakeResultMap executionStatusToBakeResultMap() {
    new ExecutionStatusToBakeResultMap()
  }

  static class ExecutionStatusToBakeResultMap {
    List<ExecutionStatusToBakeResult> associations

    public BakeStatus.Result convertExecutionStatusToBakeResult(String executionStatus) {
      associations.find {
        it.executionStatus == executionStatus
      }?.bakeResult
    }
  }

  static class ExecutionStatusToBakeResult {
    String executionStatus
    BakeStatus.Result bakeResult
  }

}

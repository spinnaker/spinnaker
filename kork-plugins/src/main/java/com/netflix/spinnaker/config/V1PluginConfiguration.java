/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.config;

import static com.netflix.spinnaker.kork.plugins.PackageKt.FRAMEWORK_V1;

import com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor;
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginFactory;
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager;
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider;
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory;
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect;
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationState;
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory;
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager;
import com.netflix.spinnaker.kork.plugins.update.release.provider.PluginInfoReleaseProvider;
import java.util.List;
import org.pf4j.PluginFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    value = "spinnaker.extensibility.framework.version",
    havingValue = FRAMEWORK_V1,
    matchIfMissing = true)
public class V1PluginConfiguration {

  @Bean
  public static PluginFactory pluginFactory(
      List<SdkFactory> sdkFactories, ConfigFactory configFactory) {
    return new SpinnakerPluginFactory(sdkFactories, configFactory);
  }

  @Bean
  public static ExtensionBeanDefinitionRegistryPostProcessor pluginBeanPostProcessor(
      SpinnakerPluginManager pluginManager,
      SpinnakerUpdateManager updateManager,
      PluginInfoReleaseProvider pluginInfoReleaseProvider,
      SpringPluginStatusProvider springPluginStatusProvider,
      ApplicationEventPublisher applicationEventPublisher,
      List<InvocationAspect<? extends InvocationState>> invocationAspects) {
    return new ExtensionBeanDefinitionRegistryPostProcessor(
        pluginManager,
        updateManager,
        pluginInfoReleaseProvider,
        springPluginStatusProvider,
        applicationEventPublisher,
        invocationAspects);
  }
}

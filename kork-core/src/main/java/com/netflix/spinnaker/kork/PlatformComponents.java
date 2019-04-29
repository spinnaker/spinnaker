/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork;

import com.netflix.spinnaker.kork.archaius.ArchaiusConfiguration;
import com.netflix.spinnaker.kork.aws.AwsComponents;
import com.netflix.spinnaker.kork.dynamicconfig.TransientConfigConfiguration;
import com.netflix.spinnaker.kork.eureka.EurekaComponents;
import com.netflix.spinnaker.kork.metrics.SpectatorConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
  ArchaiusConfiguration.class,
  TransientConfigConfiguration.class,
  EurekaComponents.class,
  SpectatorConfiguration.class,
  AwsComponents.class
})
public class PlatformComponents {}

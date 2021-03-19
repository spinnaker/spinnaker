/*
 * Copyright (c) 2018 Nike, inc.
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

package com.netflix.kayenta.standalonecanaryanalysis.config;

import com.netflix.kayenta.standalonecanaryanalysis.event.StandaloneCanaryAnalysisExecutionCompletedEvent;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("kayenta.standalone-canary-analysis.enabled")
@ComponentScan({"com.netflix.kayenta.standalonecanaryanalysis"})
@Slf4j
public class StandaloneCanaryAnalysisModuleConfiguration {
  @Bean
  @Qualifier("pre-scape-archive-hook")
  Consumer<StandaloneCanaryAnalysisExecutionCompletedEvent> preScapeArchiveHook() {
    return (event) -> log.debug("no-op pre-SCAPE archive hook");
  }
}

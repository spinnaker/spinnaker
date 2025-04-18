/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("pipeline-cache")
public class PipelineCacheConfigurationProperties {
  /** parallelism in the ForkJoinPool used to process pipelines from front50 */
  private int parallelism = Runtime.getRuntime().availableProcessors();

  /**
   * If false, query front50 for all pipelines. If true, query front50 for only enabled pipelines
   * with enabled triggers with types that echo requires.
   *
   * <p>DO NOT enable this if using cron/jenkins triggers. There's a known bug with this which
   * impacts some downsream pipeline triggers. Pipeline Templated triggers ALSO break with this set
   * on. Once tests & fixes are applied can set default.
   */
  private boolean filterFront50Pipelines = false;
}

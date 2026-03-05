/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.halyard.config.v1;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

/** Web configuration class for customizing Spring MVC behavior. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  /**
   * Configure the path matching options for Spring MVC.
   *
   * <p>This method sets a custom {@link PathPatternParser} with {@code
   * matchOptionalTrailingSeparator} enabled. This allows Spring MVC controllers to handle requests
   * with or without a trailing slash using the same mapping.
   *
   * @param configurer the {@link PathMatchConfigurer} to customize
   */
  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    PathPatternParser parser = new PathPatternParser();
    parser.setMatchOptionalTrailingSeparator(true);
    configurer.setPatternParser(parser);
  }
}

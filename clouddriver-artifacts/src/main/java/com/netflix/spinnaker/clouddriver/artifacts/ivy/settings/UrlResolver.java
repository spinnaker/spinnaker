/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.ivy.settings;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ivy.plugins.resolver.URLResolver;

@EqualsAndHashCode(callSuper = true)
@Data
public class UrlResolver extends Resolver<URLResolver> {
  @JacksonXmlProperty(isAttribute = true)
  @Nullable
  private Boolean m2compatible;

  /** Defines a pattern for Ivy files, using the pattern attribute. */
  @JacksonXmlElementWrapper(useWrapping = false)
  @Nullable
  private List<Pattern> ivy;

  /** Defines a pattern for artifacts, using the pattern attribute */
  @JacksonXmlElementWrapper(useWrapping = false)
  private List<Pattern> artifact;

  @Override
  public URLResolver toIvyModel() {
    URLResolver urlResolver = new URLResolver();
    if (m2compatible != null) {
      urlResolver.setM2compatible(m2compatible);
    }
    if (ivy != null) {
      ivy.forEach(pattern -> urlResolver.addIvyPattern(pattern.getPattern()));
    }
    artifact.forEach(pattern -> urlResolver.addArtifactPattern(pattern.getPattern()));
    return super.toIvyModel(urlResolver);
  }
}

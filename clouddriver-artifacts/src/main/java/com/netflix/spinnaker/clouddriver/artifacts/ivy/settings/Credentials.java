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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import javax.annotation.Nullable;

@Data
public class Credentials {
  @JacksonXmlProperty(isAttribute = true)
  private String host;

  @Nullable
  @JacksonXmlProperty(isAttribute = true)
  private String realm;

  @JacksonXmlProperty(isAttribute = true)
  private String username;

  @JacksonXmlProperty(isAttribute = true)
  private String password;
}

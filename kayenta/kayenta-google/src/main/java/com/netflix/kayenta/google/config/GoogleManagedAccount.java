/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.google.config;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.netflix.kayenta.security.AccountCredentials;
import jakarta.validation.constraints.NotNull;
import java.io.*;
import java.util.List;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class GoogleManagedAccount {

  @NotNull private String name;

  @NotNull private String project;
  private String jsonPath;

  private String bucket;
  private String bucketLocation;
  private String rootFolder = "kayenta";

  private List<AccountCredentials.Type> supportedTypes;

  private InputStream getInputStream() throws FileNotFoundException {
    if (StringUtils.hasLength(jsonPath)) {
      if (jsonPath.startsWith("classpath:")) {
        return getClass().getResourceAsStream(jsonPath.replace("classpath:", ""));
      } else {
        return new FileInputStream(new File(jsonPath));
      }
    } else {
      return null;
    }
  }

  public String getJsonKey() throws IOException {
    InputStream inputStream = getInputStream();

    return inputStream != null
        ? CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8))
        : null;
  }
}

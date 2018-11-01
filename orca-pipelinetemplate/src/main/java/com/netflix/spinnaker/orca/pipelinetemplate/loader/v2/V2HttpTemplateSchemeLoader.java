/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.loader.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import static java.lang.String.format;

@Component
public class V2HttpTemplateSchemeLoader implements V2TemplateSchemeLoader {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper objectMapper;
  private final OkHttpClient okHttpClient;

  // TODO(jacobkiefer): Figure out a way to secure these calls.
  // TODO(jacobkiefer): Use Artifact resolution instead of custom template loaders.
  @Autowired
  public V2HttpTemplateSchemeLoader(ObjectMapper pipelineTemplateObjectMapper) {
    this.objectMapper = pipelineTemplateObjectMapper;
    this.okHttpClient = new OkHttpClient();
  }

  @Override
  public boolean supports(URI uri) {
    String scheme = uri.getScheme();
    return scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https");
  }

  @Override
  public V2PipelineTemplate load(URI uri) {
    log.debug("Resolving pipeline template: {}", uri.toString());

    Request request = new Request.Builder().url(convertToUrl(uri)).build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new FileNotFoundException(format(
          "Received unsuccessful status code from template (code: %s, url: %s)", response.code(), uri.toString()
        ));
      }
      ResponseBody body = Optional.ofNullable(response.body())
        .orElseThrow(() -> new TemplateLoaderException("Endpoint returned an empty body"));
      String strBody = body.string();

      log.debug("Loaded Template ({}):\n{}", uri, strBody);

      return objectMapper.readValue(strBody, V2PipelineTemplate.class);
    } catch (Exception e) {
      throw new TemplateLoaderException(e);
    }
  }

  private URL convertToUrl(URI uri) {
    try {
      return uri.toURL();
    } catch (MalformedURLException e) {
      throw new TemplateLoaderException(e);
    }
  }
}

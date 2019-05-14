/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.loader;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpTemplateSchemeLoader implements TemplateSchemeLoader {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper jsonObjectMapper;
  private final ObjectMapper yamlObjectMapper;
  private final OkHttpClient okHttpClient;

  @Autowired
  public HttpTemplateSchemeLoader(ObjectMapper pipelineTemplateObjectMapper) {
    this.jsonObjectMapper = pipelineTemplateObjectMapper;

    this.yamlObjectMapper =
        new ObjectMapper(new YAMLFactory())
            .setConfig(jsonObjectMapper.getSerializationConfig())
            .setConfig(jsonObjectMapper.getDeserializationConfig());

    this.okHttpClient = new OkHttpClient();
  }

  @Override
  public boolean supports(URI uri) {
    String scheme = uri.getScheme();
    return scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https");
  }

  @Override
  public PipelineTemplate load(URI uri) {
    log.debug("Resolving pipeline template: {}", uri.toString());

    Request request = new Request.Builder().url(convertToUrl(uri)).build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new FileNotFoundException(
            format(
                "Received unsuccessful status code from template (code: %s, url: %s)",
                response.code(), uri.toString()));
      }
      ResponseBody body =
          Optional.ofNullable(response.body())
              .orElseThrow(() -> new TemplateLoaderException("Endpoint returned an empty body"));
      String strBody = body.string();

      log.debug("Loaded Template ({}):\n{}", uri, strBody);
      ObjectMapper objectMapper = isJson(uri) ? jsonObjectMapper : yamlObjectMapper;

      return objectMapper.readValue(strBody, PipelineTemplate.class);
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

/*
 * Copyright 2020 Apple, Inc.
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

package com.netflix.spinnaker.igor.config;

import com.amazonaws.util.IOUtils;
import com.google.gson.Gson;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.helm.accounts.HelmAccounts;
import com.netflix.spinnaker.igor.helm.accounts.HelmAccountsService;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import java.io.IOException;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.converter.GsonConverter;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

@Configuration
@ConditionalOnProperty("helm.enabled")
@Slf4j
public class HelmConfig {
  @Bean
  HelmAccounts helmAccounts() {
    return new HelmAccounts();
  }

  // Custom converter to deal with index file raw string responses
  class StringConverter implements Converter {
    private GsonConverter gson = new GsonConverter(new Gson());

    @Override
    public Object fromBody(TypedInput body, Type type) throws ConversionException {
      // If the return type is a String, provide it as such
      if (type.getTypeName().equals("java.lang.String")) {
        try {
          return IOUtils.toString(body.in());
        } catch (IOException e) {
          throw new ConversionException("Cannot convert response to string");
        }
      } else {
        return gson.fromBody(body, type);
      }
    }

    @Override
    public TypedOutput toBody(Object object) {
      return gson.toBody(object);
    }
  }

  @Bean
  HelmAccountsService helmAccountsService(
      OkHttp3ClientConfiguration okHttp3ClientConfig,
      IgorConfigurationProperties igorConfigurationProperties,
      RestAdapter.LogLevel retrofitLogLevel) {
    String address = igorConfigurationProperties.getServices().getClouddriver().getBaseUrl();

    if (StringUtils.isEmpty(address)) {
      log.warn(
          "No Clouddriver URL is configured - Igor will be unable to fetch Helm charts and repository indexes");
    }

    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(address))
        .setClient(new Ok3Client(okHttp3ClientConfig.create().build()))
        .setConverter(new StringConverter())
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(HelmAccountsService.class))
        .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
        .build()
        .create(HelmAccountsService.class);
  }
}

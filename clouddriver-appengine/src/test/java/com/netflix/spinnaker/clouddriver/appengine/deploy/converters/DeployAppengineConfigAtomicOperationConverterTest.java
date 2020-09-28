/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.appengine.deploy.converters;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineConfigDescription;
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class DeployAppengineConfigAtomicOperationConverterTest {

  DeployAppengineConfigAtomicOperationConverter converter;
  AccountCredentialsProvider accountCredentialsProvider;
  AppengineNamedAccountCredentials mockCredentials;
  ObjectMapper mapper;

  @Before
  public void init() {
    converter = new DeployAppengineConfigAtomicOperationConverter();
    accountCredentialsProvider = mock(AccountCredentialsProvider.class);
    mockCredentials = mock(AppengineNamedAccountCredentials.class);
    mapper = new ObjectMapper();

    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(mapper);
  }

  @Test
  public void convertDescriptionShouldSucceed() {

    Map<String, Object> stage = new HashMap<>();
    stage.put(
        "cronArtifact",
        ImmutableMap.of(
            "type", "http/file",
            "reference", "url.com",
            "artifactAccount", "httpacc"));
    stage.put("account", "appengineacc");

    when(accountCredentialsProvider.getCredentials(any())).thenReturn(mockCredentials);
    DeployAppengineConfigDescription description = converter.convertDescription(stage);
    assertTrue(description.getAccountName().equals("appengineacc"));
    assertTrue(description.getCronArtifact() != null);
  }
}

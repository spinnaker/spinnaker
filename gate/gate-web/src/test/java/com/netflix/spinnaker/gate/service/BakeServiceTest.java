/*
 * Copyright 2025 Salesforce, Inc.
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
package com.netflix.spinnaker.gate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.services.BakeService;
import com.netflix.spinnaker.gate.services.internal.RoscoService;
import com.netflix.spinnaker.gate.services.internal.RoscoServiceSelector;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import retrofit2.mock.Calls;

class BakeServiceTest {

  private ObjectMapper objectMapper = new ObjectMapper();

  private RoscoServiceSelector roscoServiceSelector = mock(RoscoServiceSelector.class);

  private RoscoService roscoService = mock(RoscoService.class);

  private BakeService bakeService = new BakeService(Optional.of(roscoServiceSelector));

  private BakeService.BakeOptions bakeOption;

  private List<BakeService.BaseImage> baseImages;

  private List<BakeService.BakeOptions> bakeOptions;

  @BeforeEach
  void init(TestInfo testInfo) throws JsonProcessingException {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    when(roscoServiceSelector.withLocation(any())).thenReturn(roscoService);

    bakeOption = new BakeService.BakeOptions();
    bakeOption.setCloudProvider("my-cloud-provider");

    BakeService.BaseImage baseImage = new BakeService.BaseImage();
    baseImage.setId("image-id");
    baseImage.setShortDescription("short description");
    baseImage.setDetailedDescription("detailed description");
    baseImage.setDisplayName("display name");
    baseImage.setPackageType("package type");
    baseImage.setVmTypes(List.of("my-vm-type"));

    baseImages = List.of(baseImage);
    bakeOption.setBaseImages(baseImages);

    bakeOptions = List.of(bakeOption);
  }

  @Test
  void testBakeOptions() {
    // given
    when(roscoService.bakeOptions()).thenReturn(Calls.response(bakeOptions));

    // when
    List<BakeService.BakeOptions> result =
        (List<BakeService.BakeOptions>) bakeService.bakeOptions();

    // then
    verify(roscoServiceSelector).withLocation(null);
    verify(roscoService).bakeOptions();
  }

  @Test
  void testBakeOptionsWithCloudProvider() {
    // Something is fishy here.
    //
    // When roscoServiceSelector is null,
    // BakeService.bakeOptions(String cloudProvider) fairly clearly returns a
    // BakeOptions object (the first element of the bakeOptions member with a
    // matching cloud provider).
    //
    // When roscoServiceSelector isn't null, BakeService.bakeOptions(String cloudProvider) returns
    // whatever RoscoService.bakeOptions(cloudProvider) returns, which is a Map.
    //
    // Presumably the http response from GET /bakery/options/{cloudProvider} has
    // the same structure either way.  If the return value of
    // BakeController.bakeOptions(String cloudProvider) (which is the return
    // value of BakeService.bakeOptions(String cloudProvider)) serializes to the
    // same structure, it does.
    //
    // Groovy is allowing this.  By converting BakeService to java, we'd have to
    // choose a return type for BakeService.bakeOptions(String cloudProvider).
    // Pretty sure that would be BakeOptions, and then we'd have a choice about
    // whether to change the return type of RoscoService.bakeOptions(String
    // cloudProvider) to match (my preference), or leave it returning a Map and
    // convert in BakeService.bakeOptions.  The corresponding rosco code is at
    // https://github.com/spinnaker/rosco/blob/2f62f092e0a14bd10f204987c497034f54a46182/rosco-web/src/main/groovy/com/netflix/spinnaker/rosco/controllers/BakeryController.groovy#L75,
    // and the return type from rosco is
    // https://github.com/spinnaker/rosco/blob/2f62f092e0a14bd10f204987c497034f54a46182/rosco-core/src/main/groovy/com/netflix/spinnaker/rosco/api/BakeOptions.groovy,
    // which is out of sync with the corresponding type in gate's BakeService,
    // but it's close enough that it's worth a shot.
    //
    // For now since this test is java, and we're explicitly testing with roscoServiceSelector not
    // null, let's convert from a Map to BakeOptions.

    // given
    String cloudProvider = "cloud-provider";
    Map<String, Object> bakeOptionsMap =
        objectMapper.convertValue(bakeOption, new TypeReference<>() {});
    when(roscoService.bakeOptions(cloudProvider)).thenReturn(Calls.response(bakeOptionsMap));

    // when
    Object resultObj = bakeService.bakeOptions(cloudProvider);

    // then
    verify(roscoServiceSelector).withLocation(null);
    verify(roscoService).bakeOptions(cloudProvider);

    BakeService.BakeOptions result =
        objectMapper.convertValue(resultObj, BakeService.BakeOptions.class);
    assertThat(result).usingRecursiveComparison().isEqualTo(bakeOption);
  }

  @Test
  void testLookupLogs() {
    // given
    String region = "my-region";
    String statusId = "my-status-id";

    String roscoLog = "rosco-log";
    Map<String, String> roscoResult = Map.of("logsContent", roscoLog);

    when(roscoService.lookupLogs(region, statusId)).thenReturn(Calls.response(roscoResult));

    // when
    String result = bakeService.lookupLogs(region, statusId);

    // then
    verify(roscoServiceSelector).withLocation(region);
    verify(roscoService).lookupLogs(region, statusId);

    assertThat(result).isEqualTo("<pre>" + roscoLog + "</pre>");
  }
}

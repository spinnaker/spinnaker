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
package com.netflix.spinnaker.orca.clouddriver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.web.selector.SelectableService;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DelegatingKatoRestServiceTest {

  @Mock SelectableService<KatoRestService> selectableService;

  @Mock KatoRestService katoRestService;

  DelegatingKatoRestService delegatingKatoRestService;

  @BeforeEach
  void setup() {
    when(selectableService.getService(any())).thenReturn(katoRestService);
    delegatingKatoRestService = new DelegatingKatoRestService(selectableService);
  }

  @Test
  void requestOperationsParameterOrder() {
    // given
    String cloudProvider = "cloudProvider";
    String clientRequestId = "clientRequestId";
    Collection<Map<String, Map>> operations = Collections.emptyList();

    // when
    delegatingKatoRestService.requestOperations(cloudProvider, clientRequestId, operations);

    // then
    // FIXME: KatoRestService.requestOperations expects cloudProvider as the first argument, then
    // clientRequestId
    // verify(katoRestService).requestOperations(cloudProvider, clientRequestId, operations);
    verify(katoRestService).requestOperations(clientRequestId, cloudProvider, operations);
    verifyNoMoreInteractions(katoRestService);
  }
}

/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client;

import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

public class TitusClientAuthenticationUtil {

  private static String CALLER_ID_HEADER = "X-Titus-CallerId";
  private static String CALL_REASON = "X-Titus-CallReason";
  private static Metadata.Key<String> CALLER_ID_KEY =
      Metadata.Key.of(CALLER_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);
  private static Metadata.Key<String> CALL_REASON_KEY =
      Metadata.Key.of(CALL_REASON, Metadata.ASCII_STRING_MARSHALLER);

  public static <STUB extends AbstractStub<STUB>> STUB attachCaller(STUB serviceStub) {
    Metadata metadata = new Metadata();
    metadata.put(CALLER_ID_KEY, AuthenticatedRequest.getSpinnakerUser().orElse("spinnaker"));
    metadata.put(CALL_REASON_KEY, AuthenticatedRequest.getSpinnakerExecutionId().orElse("unknown"));
    return serviceStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
  }
}

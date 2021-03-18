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

import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

public class TitusClientCompressionUtil {

  private static String COMPRESSION_HEADER = "X-Titus-Compression";
  private static Metadata.Key<String> COMPRESSION_KEY =
      Metadata.Key.of(COMPRESSION_HEADER, Metadata.ASCII_STRING_MARSHALLER);

  public static <STUB extends AbstractStub<STUB>> STUB attachCaller(STUB serviceStub) {
    Metadata metadata = new Metadata();
    metadata.put(COMPRESSION_KEY, "gzip");
    return serviceStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
  }
}

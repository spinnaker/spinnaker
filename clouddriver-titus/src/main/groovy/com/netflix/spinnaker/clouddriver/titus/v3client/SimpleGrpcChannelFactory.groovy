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

package com.netflix.spinnaker.clouddriver.titus.v3client

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.GrpcChannelFactory
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder

class SimpleGrpcChannelFactory implements GrpcChannelFactory {
  @Override
  ManagedChannel build(TitusRegion titusRegion, String environment, String eurekaName, long defaultConnectTimeOut, Registry registry) {
    return NettyChannelBuilder
      .forAddress(titusRegion.endpoint, 7104)
      .negotiationType(NegotiationType.TLS)
      .maxHeaderListSize(65536)
      .maxInboundMessageSize(65536)
      .build()
  }
}

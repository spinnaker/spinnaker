/*
 * Copyright 2019 Netflix, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.titus.client.model;

import lombok.Data;

@Data
public class SignedAddressAllocations {
  private AddressAllocation addressAllocation;
  private String authoritativePublicKey;
  private String hostPublicKey;
  private String hostPublicKeySignature;
  private String message;
  private String messageSignature;

  @Data
  public static class AddressAllocation {
    private AddressLocation addressLocation;
    private String uuid;
    private String address;
  }

  @Data
  public static class AddressLocation {
    private String region;
    private String availabilityZone;
    private String subnetId;
  }
}

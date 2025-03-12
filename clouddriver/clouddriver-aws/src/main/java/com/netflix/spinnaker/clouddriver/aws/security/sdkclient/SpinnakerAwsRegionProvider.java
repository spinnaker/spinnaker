/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.regions.AwsRegionProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.regions.Regions;

public class SpinnakerAwsRegionProvider extends AwsRegionProviderChain {

  public SpinnakerAwsRegionProvider() {
    super(
        new Ec2RegionEnvVarRegionProvider(),
        new DefaultAwsRegionProviderChain(),
        new RegionsCurrentRegionProvider(),
        new DefaultRegionProvider());
  }

  private static class Ec2RegionEnvVarRegionProvider extends AwsRegionProvider {
    @Override
    public String getRegion() throws SdkClientException {
      return System.getenv("EC2_REGION");
    }
  }

  private static class RegionsCurrentRegionProvider extends AwsRegionProvider {
    @Override
    public String getRegion() throws SdkClientException {
      return Regions.getCurrentRegion().getName();
    }
  }

  private static class DefaultRegionProvider extends AwsRegionProvider {
    @Override
    public String getRegion() throws SdkClientException {
      return Regions.DEFAULT_REGION.getName();
    }
  }
}

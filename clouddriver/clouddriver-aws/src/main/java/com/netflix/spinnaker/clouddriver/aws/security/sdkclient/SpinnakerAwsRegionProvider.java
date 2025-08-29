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
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.AwsProfileRegionProvider;
import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.regions.AwsRegionProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.regions.Regions;
import java.lang.reflect.Field;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpinnakerAwsRegionProvider extends AwsRegionProviderChain {

  public SpinnakerAwsRegionProvider(AWSCredentialsProvider credentialsProvider) {
    super(
        new Ec2RegionEnvVarRegionProvider(),
        new ProfileCredentialsProviderRegionProvider(credentialsProvider),
        new DefaultAwsRegionProviderChain(),
        new RegionsCurrentRegionProvider(),
        new DefaultRegionProvider());
  }

  private static class Ec2RegionEnvVarRegionProvider extends AwsRegionProvider {
    @Override
    public String getRegion() throws SdkClientException {
      String region = System.getenv("EC2_REGION");
      log.debug("Ec2RegionEnvVarRegionProvider: region '{}'", region);
      return region;
    }
  }

  private static class ProfileCredentialsProviderRegionProvider extends AwsRegionProvider {

    /** Determine the region associated with this profile */
    private final String profileName;

    private final AwsProfileRegionProvider awsProfileRegionProvider;

    ProfileCredentialsProviderRegionProvider(AWSCredentialsProvider credentialsProvider) {
      if (credentialsProvider instanceof ProfileCredentialsProvider) {
        ProfileCredentialsProvider profileCredentialsProvider =
            (ProfileCredentialsProvider) credentialsProvider;

        // There's no accessor for profile name in ProfileCredentialsProvider so
        // use reflection.
        final Field field;
        try {
          field = profileCredentialsProvider.getClass().getDeclaredField("profileName");
        } catch (NoSuchFieldException e) {
          // wrap it so callers don't have to deal with it
          throw new RuntimeException("error getting profileName field: " + e.getMessage(), e);
        }
        field.setAccessible(true);
        try {
          this.profileName = (String) field.get(profileCredentialsProvider);
        } catch (IllegalAccessException e) {
          throw new RuntimeException("error getting profileName value: " + e.getMessage(), e);
        }
        log.debug(
            "ProfileCredentialsProviderRegionProvider: got profile name '{}' from profileCredentialsProvider",
            profileName);

        this.awsProfileRegionProvider = new AwsProfileRegionProvider(profileName);
      } else {
        this.profileName = null;
        this.awsProfileRegionProvider = null;
      }
    }

    @Override
    public String getRegion() throws SdkClientException {
      log.debug("ProfileCredentialsProviderRegionProvider: profileName: '{}'", profileName);
      if (awsProfileRegionProvider == null) {
        return null;
      }

      String region = awsProfileRegionProvider.getRegion();
      log.debug(
          "ProfileCredentialsProviderRegionProvider: profileName: '{}', region '{}'",
          profileName,
          region);
      return region;
    }
  }

  private static class RegionsCurrentRegionProvider extends AwsRegionProvider {
    @Override
    public String getRegion() throws SdkClientException {
      String region = Regions.getCurrentRegion().getName();
      log.debug("RegionsCurrentRegionProvider: region '{}'", region);
      return region;
    }
  }

  private static class DefaultRegionProvider extends AwsRegionProvider {
    @Override
    public String getRegion() throws SdkClientException {
      String region = Regions.DEFAULT_REGION.getName();
      log.debug("DefaultRegionProvider: region '{}'", region);
      return region;
    }
  }
}

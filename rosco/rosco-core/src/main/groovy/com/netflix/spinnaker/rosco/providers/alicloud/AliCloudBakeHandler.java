/*
 * Copyright 2019 Alibaba Group, Inc.
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

package com.netflix.spinnaker.rosco.providers.alicloud;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.rosco.api.Bake;
import com.netflix.spinnaker.rosco.api.BakeOptions;
import com.netflix.spinnaker.rosco.api.BakeOptions.BaseImage;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler;
import com.netflix.spinnaker.rosco.providers.alicloud.config.RoscoAliCloudConfiguration.AliCloudBakeryDefaults;
import com.netflix.spinnaker.rosco.providers.alicloud.config.RoscoAliCloudConfiguration.AliCloudOperatingSystemVirtualizationSettings;
import com.netflix.spinnaker.rosco.providers.alicloud.config.RoscoAliCloudConfiguration.AliCloudVirtualizationSettings;
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "alicloud-ecs: Creating image:";

  ImageNameFactory imageNameFactory = new ImageNameFactory();

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Autowired AliCloudBakeryDefaults alicloudBakeryDefaults;

  @Override
  public Object getBakeryDefaults() {
    return alicloudBakeryDefaults;
  }

  @Override
  public BakeOptions getBakeOptions() {
    List<AliCloudOperatingSystemVirtualizationSettings> settings =
        alicloudBakeryDefaults.getBaseImages();
    List<BaseImage> baseImages = new ArrayList<>();
    for (AliCloudOperatingSystemVirtualizationSettings baseImage : settings) {
      baseImages.add(baseImage.getBaseImage());
    }

    BakeOptions bakeOptions = new BakeOptions();
    bakeOptions.setCloudProvider(BakeRequest.CloudProviderType.alicloud.toString());
    bakeOptions.setBaseImages(baseImages);
    return bakeOptions;
  }

  @Override
  public Object findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    List<AliCloudOperatingSystemVirtualizationSettings> settings =
        alicloudBakeryDefaults.getBaseImages();
    List<AliCloudOperatingSystemVirtualizationSettings> virtualizationSettings = new ArrayList<>();
    for (AliCloudOperatingSystemVirtualizationSettings setting : settings) {
      if (setting.getBaseImage().getId().equals(bakeRequest.getBase_os())) {
        virtualizationSettings.add(setting);
      }
    }

    if (virtualizationSettings.size() == 0) {
      throw new IllegalArgumentException(
          "No virtualization settings found for '" + bakeRequest.getBase_os() + "'.");
    }

    AliCloudVirtualizationSettings alicloudVirtualizationSettings = null;

    for (AliCloudOperatingSystemVirtualizationSettings virtualizationSetting :
        virtualizationSettings) {
      for (AliCloudVirtualizationSettings setting :
          virtualizationSetting.getVirtualizationSettings()) {
        if (region.equals(setting.getRegion())) {
          alicloudVirtualizationSettings = setting;
          break;
        }
      }
    }

    if (alicloudVirtualizationSettings == null) {
      throw new IllegalArgumentException(
          "No virtualization settings found for region '"
              + region
              + "', operating system '"
              + bakeRequest.getBase_os()
              + "'.");
    }

    if (StringUtils.isNotEmpty(bakeRequest.getBase_ami())) {
      try {
        alicloudVirtualizationSettings =
            (AliCloudVirtualizationSettings) alicloudVirtualizationSettings.clone();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }

      alicloudVirtualizationSettings.setSourceImage(bakeRequest.getBase_ami());
    }

    return alicloudVirtualizationSettings;
  }

  @Override
  public String getTemplateFileName(BaseImage baseImage) {
    String f1 = baseImage.getTemplateFile();
    String f2 = alicloudBakeryDefaults.getTemplateFile();
    return StringUtils.isNotEmpty(f1) ? f1 : f2;
  }

  @Override
  public Map buildParameterMap(
      String region,
      Object virtualizationSettings,
      String imageName,
      BakeRequest bakeRequest,
      String appVersionStr) {
    Map<String, Object> parameterMap = new HashMap<>(20);
    AliCloudVirtualizationSettings aliCloudVirtualizationSettings =
        objectMapper.convertValue(virtualizationSettings, AliCloudVirtualizationSettings.class);
    parameterMap.put("alicloud_region", region);
    parameterMap.put("alicloud_instance_type", aliCloudVirtualizationSettings.getInstanceType());
    parameterMap.put("alicloud_source_image", aliCloudVirtualizationSettings.getSourceImage());
    parameterMap.put("alicloud_target_image", imageName);

    if (StringUtils.isNotEmpty(aliCloudVirtualizationSettings.getSshUserName())) {
      parameterMap.put("alicloud_ssh_username", aliCloudVirtualizationSettings.getSshUserName());
    }

    if (StringUtils.isNotEmpty(alicloudBakeryDefaults.getAlicloudAccessKey())
        && StringUtils.isNotEmpty(alicloudBakeryDefaults.getAlicloudSecretKey())) {
      parameterMap.put("alicloud_access_key", alicloudBakeryDefaults.getAlicloudAccessKey());
      parameterMap.put("alicloud_secret_key", alicloudBakeryDefaults.getAlicloudSecretKey());
    }

    if (StringUtils.isNotEmpty(alicloudBakeryDefaults.getAlicloudVSwitchId())) {
      parameterMap.put("alicloud_vswitch_id", alicloudBakeryDefaults.getAlicloudVSwitchId());
    }

    if (StringUtils.isNotEmpty(alicloudBakeryDefaults.getAlicloudVpcId())) {
      parameterMap.put("alicloud_vpc_id", alicloudBakeryDefaults.getAlicloudVpcId());
    }

    if (StringUtils.isNotEmpty(bakeRequest.getBuild_info_url())) {
      parameterMap.put("build_info_url", bakeRequest.getBuild_info_url());
    }

    if (StringUtils.isNotEmpty(appVersionStr)) {
      parameterMap.put("appversion", appVersionStr);
    }

    return parameterMap;
  }

  @Override
  public Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String amiId = "";
    String imageName = "";

    // Resolve this by storing bake details in redis and querying oort for
    // amiId from amiName.
    String[] lines = logsContent.split("\\n");
    for (String line : lines) {
      if (line.indexOf(IMAGE_NAME_TOKEN) != -1) {
        String[] s = line.split(" ");
        imageName = s[s.length - 1];
      } else if (line.indexOf(region) != -1) {
        String[] s = line.split(" ");
        amiId = s[s.length - 1];
      }
    }
    Bake bake = new Bake();
    bake.setId(bakeId);
    bake.setAmi(amiId);
    bake.setImage_name(imageName);

    return bake;
  }

  @Override
  public String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return region;
  }

  @Override
  public ImageNameFactory getImageNameFactory() {
    return imageNameFactory;
  }
}

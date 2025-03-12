/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.rosco.providers.huaweicloud;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.rosco.api.Bake;
import com.netflix.spinnaker.rosco.api.BakeOptions;
import com.netflix.spinnaker.rosco.api.BakeOptions.BaseImage;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler;
import com.netflix.spinnaker.rosco.providers.huaweicloud.config.RoscoHuaweiCloudConfiguration.HuaweiCloudBakeryDefaults;
import com.netflix.spinnaker.rosco.providers.huaweicloud.config.RoscoHuaweiCloudConfiguration.HuaweiCloudOperatingSystemVirtualizationSettings;
import com.netflix.spinnaker.rosco.providers.huaweicloud.config.RoscoHuaweiCloudConfiguration.HuaweiCloudVirtualizationSettings;
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HuaweiCloudBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "huaweicloud-ecs: Creating the image:";
  private static final String IMAGE_ID_TOKEN = "huaweicloud-ecs: An image was created:";

  ImageNameFactory imageNameFactory = new ImageNameFactory();

  @Autowired HuaweiCloudBakeryDefaults huaweicloudBakeryDefaults;

  @Override
  public Object getBakeryDefaults() {
    return huaweicloudBakeryDefaults;
  }

  @Override
  public BakeOptions getBakeOptions() {
    List<HuaweiCloudOperatingSystemVirtualizationSettings> settings =
        huaweicloudBakeryDefaults.getBaseImages();

    List<BaseImage> baseImages = new ArrayList<>();
    for (HuaweiCloudOperatingSystemVirtualizationSettings baseImage : settings) {
      baseImages.add(baseImage.getBaseImage());
    }

    BakeOptions bakeOptions = new BakeOptions();
    bakeOptions.setCloudProvider(BakeRequest.CloudProviderType.huaweicloud.toString());
    bakeOptions.setBaseImages(baseImages);
    return bakeOptions;
  }

  @Override
  public Object findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    List<HuaweiCloudOperatingSystemVirtualizationSettings> settings =
        huaweicloudBakeryDefaults.getBaseImages();

    List<HuaweiCloudOperatingSystemVirtualizationSettings> virtualizationSettings =
        new ArrayList<>();
    for (HuaweiCloudOperatingSystemVirtualizationSettings setting : settings) {
      if (setting.getBaseImage().getId().equals(bakeRequest.getBase_os())) {
        virtualizationSettings.add(setting);
      }
    }

    if (virtualizationSettings.size() == 0) {
      throw new IllegalArgumentException(
          "No virtualization settings found for '" + bakeRequest.getBase_os() + "'.");
    }

    HuaweiCloudVirtualizationSettings huaweicloudVirtualizationSettings = null;

    for (HuaweiCloudOperatingSystemVirtualizationSettings virtualizationSetting :
        virtualizationSettings) {
      for (HuaweiCloudVirtualizationSettings setting :
          virtualizationSetting.getVirtualizationSettings()) {
        if (region.equals(setting.getRegion())) {
          huaweicloudVirtualizationSettings = setting;
          break;
        }
      }
    }

    if (huaweicloudVirtualizationSettings == null) {
      throw new IllegalArgumentException(
          "No virtualization settings found for region '"
              + region
              + "', operating system '"
              + bakeRequest.getBase_os()
              + "'.");
    }

    if (StringUtils.isNotEmpty(bakeRequest.getBase_ami())) {
      try {
        huaweicloudVirtualizationSettings =
            (HuaweiCloudVirtualizationSettings) huaweicloudVirtualizationSettings.clone();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }

      huaweicloudVirtualizationSettings.setSourceImageId(bakeRequest.getBase_ami());
    }

    return huaweicloudVirtualizationSettings;
  }

  @Override
  public Map buildParameterMap(
      String region,
      Object virtualizationSettings,
      String imageName,
      BakeRequest bakeRequest,
      String appVersionStr) {

    Map<String, Object> parameterMap = new HashMap<>(20);

    parameterMap.put("huaweicloud_region", region);
    parameterMap.put("huaweicloud_image_name", imageName);

    ObjectMapper objectMapper =
        new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    HuaweiCloudVirtualizationSettings huaweicloudVirtualizationSettings =
        objectMapper.convertValue(virtualizationSettings, HuaweiCloudVirtualizationSettings.class);

    parameterMap.put("huaweicloud_eip_type", huaweicloudVirtualizationSettings.getEipType());
    parameterMap.put(
        "huaweicloud_ssh_username", huaweicloudVirtualizationSettings.getSshUserName());
    parameterMap.put(
        "huaweicloud_instance_type", huaweicloudVirtualizationSettings.getInstanceType());
    parameterMap.put(
        "huaweicloud_source_image_id", huaweicloudVirtualizationSettings.getSourceImageId());

    if (StringUtils.isNotEmpty(huaweicloudBakeryDefaults.getAuthUrl())) {
      parameterMap.put("huaweicloud_auth_url", huaweicloudBakeryDefaults.getAuthUrl());
    }

    if (StringUtils.isNotEmpty(huaweicloudBakeryDefaults.getUsername())) {
      parameterMap.put("huaweicloud_username", huaweicloudBakeryDefaults.getUsername());
    }

    if (StringUtils.isNotEmpty(huaweicloudBakeryDefaults.getPassword())) {
      parameterMap.put("huaweicloud_password", huaweicloudBakeryDefaults.getPassword());
    }

    if (StringUtils.isNotEmpty(huaweicloudBakeryDefaults.getProjectName())) {
      parameterMap.put("huaweicloud_project_name", huaweicloudBakeryDefaults.getProjectName());
    }

    if (StringUtils.isNotEmpty(huaweicloudBakeryDefaults.getDomainName())) {
      parameterMap.put("huaweicloud_domain_name", huaweicloudBakeryDefaults.getDomainName());
    }

    if (huaweicloudBakeryDefaults.getInsecure() != null) {
      parameterMap.put("huaweicloud_insecure", huaweicloudBakeryDefaults.getInsecure());
    }

    if (StringUtils.isNotEmpty(huaweicloudBakeryDefaults.getVpcId())) {
      parameterMap.put("huaweicloud_vpc_id", huaweicloudBakeryDefaults.getVpcId());
    }

    if (StringUtils.isNotEmpty(huaweicloudBakeryDefaults.getSubnetId())) {
      parameterMap.put("huaweicloud_subnet_id", huaweicloudBakeryDefaults.getSubnetId());
    }

    if (StringUtils.isNotEmpty(huaweicloudBakeryDefaults.getSecurityGroup())) {
      parameterMap.put("huaweicloud_security_group", huaweicloudBakeryDefaults.getSecurityGroup());
    }

    if (huaweicloudBakeryDefaults.getEipBandwidthSize() != null
        && huaweicloudBakeryDefaults.getEipBandwidthSize() > 0) {
      parameterMap.put(
          "huaweicloud_eip_bandwidth_size", huaweicloudBakeryDefaults.getEipBandwidthSize());
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
  public String getTemplateFileName(BaseImage baseImage) {
    String f1 = baseImage.getTemplateFile();
    String f2 = huaweicloudBakeryDefaults.getTemplateFile();
    return StringUtils.isNotEmpty(f1) ? f1 : f2;
  }

  @Override
  public String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return region;
  }

  @Override
  public ImageNameFactory getImageNameFactory() {
    return imageNameFactory;
  }

  @Override
  public Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String imageId = "";
    String imageName = "";

    String[] lines = logsContent.split("\\n");
    for (String line : lines) {
      if (line.indexOf(IMAGE_NAME_TOKEN) != -1) {
        String[] s = line.split(" ");
        imageName = s[s.length - 1];
      } else if (line.indexOf(IMAGE_ID_TOKEN) != -1) {
        String[] s = line.split(" ");
        imageId = s[s.length - 1];
        break;
      }
    }

    Bake bake = new Bake();
    bake.setId(bakeId);
    bake.setAmi(imageId);
    bake.setImage_name(imageName);
    return bake;
  }

  @Override
  public List<String> getMaskedPackerParameters() {
    List<String> r = new ArrayList<>();
    r.add("huaweicloud_password");
    return r;
  }
}

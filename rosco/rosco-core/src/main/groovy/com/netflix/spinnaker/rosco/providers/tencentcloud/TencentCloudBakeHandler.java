/*
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.rosco.providers.tencentcloud;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.rosco.api.Bake;
import com.netflix.spinnaker.rosco.api.BakeOptions;
import com.netflix.spinnaker.rosco.api.BakeOptions.BaseImage;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.api.BakeRequest.CloudProviderType;
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler;
import com.netflix.spinnaker.rosco.providers.tencentcloud.config.RoscoTencentCloudConfiguration.TencentCloudBakeryDefaults;
import com.netflix.spinnaker.rosco.providers.tencentcloud.config.RoscoTencentCloudConfiguration.TencentCloudOperatingSystemVirtualizationSettings;
import com.netflix.spinnaker.rosco.providers.tencentcloud.config.RoscoTencentCloudConfiguration.TencentCloudVirtualizationSettings;
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TencentCloudBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "create a new image:";

  private ImageNameFactory imageNameFactory = new ImageNameFactory();
  @Autowired TencentCloudBakeryDefaults tencentCloudBakeryDefaults;

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Override
  public TencentCloudBakeryDefaults getBakeryDefaults() {
    return tencentCloudBakeryDefaults;
  }

  @Override
  public BakeOptions getBakeOptions() {
    List<BaseImage> baseImageList = new ArrayList<>();
    if (tencentCloudBakeryDefaults != null && tencentCloudBakeryDefaults.getBaseImages() != null) {
      for (TencentCloudOperatingSystemVirtualizationSettings settings :
          tencentCloudBakeryDefaults.getBaseImages()) {
        baseImageList.add(settings.getBaseImage());
      }
    }
    BakeOptions options = new BakeOptions();
    options.setCloudProvider(CloudProviderType.tencentcloud.toString());
    options.setBaseImages(baseImageList);
    return options;
  }

  @Override
  public String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return region;
  }

  @Override
  public TencentCloudVirtualizationSettings findVirtualizationSettings(
      String region, BakeRequest bakeRequest) {

    TencentCloudOperatingSystemVirtualizationSettings operatingSystemVirtualizationSettings =
        tencentCloudBakeryDefaults.getBaseImages().stream()
            .filter(it -> it.getBaseImage().getId().equals(bakeRequest.getBase_os()))
            .findAny()
            .orElse(null);

    if (operatingSystemVirtualizationSettings == null) {
      throw new IllegalArgumentException(
          "No virtualization settings found for '" + bakeRequest.getBase_os() + "'.");
    }

    TencentCloudVirtualizationSettings virtualizationSettings =
        operatingSystemVirtualizationSettings.getVirtualizationSettings().stream()
            .filter(it -> it.getRegion().equals(region))
            .findAny()
            .orElse(null);

    if (virtualizationSettings == null) {
      throw new IllegalArgumentException(
          "No virtualization settings found for region '"
              + region
              + "', operating system '"
              + bakeRequest.getBase_os()
              + "'");
    }

    if (bakeRequest.getBase_ami() != null) {
      try {
        virtualizationSettings =
            (TencentCloudVirtualizationSettings) virtualizationSettings.clone();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }
      virtualizationSettings.setSourceImageId(bakeRequest.getBase_ami());
    }

    return virtualizationSettings;
  }

  @Override
  public Map buildParameterMap(
      String region,
      Object virtualizationSettings,
      String imageName,
      BakeRequest bakeRequest,
      String appVersionStr) {
    TencentCloudVirtualizationSettings tencentCloudVirtualizationSettings =
        objectMapper.convertValue(virtualizationSettings, TencentCloudVirtualizationSettings.class);

    Map<String, String> parameterMap = new HashMap<>();
    parameterMap.put("tencentcloud_region", region);
    parameterMap.put("tencentcloud_zone", tencentCloudVirtualizationSettings.getZone());
    parameterMap.put(
        "tencentcloud_instance_type", tencentCloudVirtualizationSettings.getInstanceType());
    parameterMap.put(
        "tencentcloud_source_image_id", tencentCloudVirtualizationSettings.getSourceImageId());
    parameterMap.put("tencentcloud_target_image", imageName);

    if (StringUtils.isNotBlank(tencentCloudVirtualizationSettings.getSshUserName())) {
      parameterMap.put(
          "tencentcloud_ssh_username", tencentCloudVirtualizationSettings.getSshUserName());
    }

    if (StringUtils.isNotBlank(tencentCloudBakeryDefaults.getSecretId())
        && StringUtils.isNotBlank(tencentCloudBakeryDefaults.getSecretKey())) {
      parameterMap.put("tencentcloud_secret_id", tencentCloudBakeryDefaults.getSecretId());
      parameterMap.put("tencentcloud_secret_key", tencentCloudBakeryDefaults.getSecretKey());
    }

    if (StringUtils.isNotBlank(tencentCloudBakeryDefaults.getSubnetId())) {
      parameterMap.put("tencentcloud_subnet_id", tencentCloudBakeryDefaults.getSubnetId());
    }

    if (StringUtils.isNotBlank(tencentCloudBakeryDefaults.getVpcId())) {
      parameterMap.put("tencentcloud_vpc_id", tencentCloudBakeryDefaults.getVpcId());
    }

    if (tencentCloudBakeryDefaults.getAssociatePublicIpAddress() != null) {
      parameterMap.put(
          "tencentcloud_associate_public_ip_address",
          tencentCloudBakeryDefaults.getAssociatePublicIpAddress().toString());
    }

    if (StringUtils.isNotBlank(appVersionStr)) {
      parameterMap.put("appversion", appVersionStr);
    }

    return parameterMap;
  }

  @Override
  public String getTemplateFileName(BaseImage baseImage) {
    String file = baseImage.getTemplateFile();
    return StringUtils.isEmpty(file) ? tencentCloudBakeryDefaults.getTemplateFile() : file;
  }

  @Override
  public Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    log.info("ScrapeCompletedBakeResults, with " + region + ", " + bakeId + ", " + logsContent);
    String imageId = "";
    String imageName = "";

    String[] lines = logsContent.split("\\n");
    for (String line : lines) {
      if (line.contains(IMAGE_NAME_TOKEN)) {
        // Packer log example
        // ==> tencentcloud-cvm: Trying to create a new image: image-name...
        String[] s = line.split(" ");
        imageName = s[s.length - 1];
        imageName = imageName.substring(0, imageName.length() - 3);
      } else if (line.contains(region)) {
        // Packer log example
        // --> tencentcloud-cvm: Tencentcloud images(ap-guangzhou: img-12345678) were created.
        String[] s = line.split(" ");
        imageId = s[s.length - 3];
        imageId = imageId.substring(0, imageId.length() - 1);
      }
    }

    Bake bake = new Bake();
    bake.setId(bakeId);
    bake.setAmi(imageId);
    bake.setImage_name(imageName);

    return bake;
  }

  public ImageNameFactory getImageNameFactory() {
    return imageNameFactory;
  }
}

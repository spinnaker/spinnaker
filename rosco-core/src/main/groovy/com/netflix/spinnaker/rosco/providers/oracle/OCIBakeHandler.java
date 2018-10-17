/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.rosco.providers.oracle;

import com.amazonaws.util.StringUtils;
import com.google.common.base.Strings;
import com.netflix.spinnaker.rosco.api.Bake;
import com.netflix.spinnaker.rosco.api.BakeOptions;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler;
import com.netflix.spinnaker.rosco.providers.oracle.config.ManagedOracleAccount;
import com.netflix.spinnaker.rosco.providers.oracle.config.OracleBakeryDefaults;
import com.netflix.spinnaker.rosco.providers.oracle.config.OracleConfigurationProperties;
import com.netflix.spinnaker.rosco.providers.oracle.config.OracleOperatingSystemVirtualizationSettings;
import com.netflix.spinnaker.rosco.providers.oracle.config.OracleVirtualizationSettings;
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

@Component
public class OCIBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "oracle-oci: An image was created:";

  ImageNameFactory imageNameFactory = new ImageNameFactory();

  @Autowired
  OracleBakeryDefaults oracleBakeryDefaults;

  @Autowired
  OracleConfigurationProperties oracleConfigurationProperties;

  @Override
  public Object getBakeryDefaults() {
    return oracleBakeryDefaults;
  }

  @Override
  public BakeOptions getBakeOptions() {
    List<BakeOptions.BaseImage> baseImages;
    if (oracleBakeryDefaults.getBaseImages().size() > 0) {
      baseImages = oracleBakeryDefaults.getBaseImages().stream()
              .map(OracleOperatingSystemVirtualizationSettings::getBaseImage)
              .collect(Collectors.toList());
    } else {
      baseImages = new ArrayList<>();
    }
    BakeOptions options = new BakeOptions();
    options.setCloudProvider(BakeRequest.CloudProviderType.oracle.name());
    options.setBaseImages(baseImages);
    return options;
  }

  @Override
  public ImageNameFactory getImageNameFactory() {
    return imageNameFactory;
  }

  @Override
  public String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return resolveAccount(bakeRequest).getName();
  }

  @Override
  public Object findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    if (oracleBakeryDefaults.getBaseImages().size() > 0) {
      OracleOperatingSystemVirtualizationSettings settings = oracleBakeryDefaults.getBaseImages().stream()
              .filter(baseImage -> bakeRequest.getBase_os().equals(baseImage.getBaseImage().getId()))
              .findAny()
              .orElse(null);
      if (settings != null) {
        return settings.getVirtualizationSettings();
      }
    }

    throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.");
  }

  @Override
  public Map buildParameterMap(String region, Object virtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    OracleVirtualizationSettings oracleVirtualizationSettings = (OracleVirtualizationSettings) virtualizationSettings;

    ManagedOracleAccount managedAccount = resolveAccount(bakeRequest);

    Map<String, String> parameterMap = new HashMap<String, String>() {
      {
        put("oracle_compartment_id", managedAccount.getCompartmentId());
        put("oracle_tenancy_id", managedAccount.getTenancyId());
        put("oracle_user_id", managedAccount.getUserId());
        put("oracle_fingerprint", managedAccount.getFingerprint());
        put("oracle_ssh_private_key_file_path", managedAccount.getSshPrivateKeyFilePath());
        put("oracle_pass_phrase", managedAccount.getPrivateKeyPassphrase());
        put("oracle_region", managedAccount.getRegion());
        put("oracle_availability_domain", oracleBakeryDefaults.getAvailabilityDomain());
        put("oracle_instance_shape", oracleBakeryDefaults.getInstanceShape());
        put("oracle_subnet_id", oracleBakeryDefaults.getSubnetId());
        put("oracle_base_image_id", oracleVirtualizationSettings.getBaseImageId());
        put("oracle_ssh_user_name", oracleVirtualizationSettings.getSshUserName());
        put("oracle_image_name", imageName);
      }
    };


    if (!StringUtils.isNullOrEmpty(bakeRequest.getBuild_info_url())) {
      parameterMap.put("build_info_url", bakeRequest.getBuild_info_url());
    }

    if (!StringUtils.isNullOrEmpty(appVersionStr)) {
      parameterMap.put("appversion", appVersionStr);
    }

    return parameterMap;
  }

  @Override
  public String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    if (baseImage != null && !Strings.isNullOrEmpty(baseImage.getTemplateFile())) {
      return baseImage.getTemplateFile();
    } else {
      return oracleBakeryDefaults.getTemplateFile();
    }
  }

  @Override
  public Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    Bake bake = new Bake();
    bake.setId(bakeId);
    Scanner scanner = new Scanner(logsContent);
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      if (line.contains(IMAGE_NAME_TOKEN)) {
        String[] elements = line.split("'");
        if (elements.length > 2) {
          bake.setImage_name(elements[1]);
          String ocidPart = elements[2].trim();
          if (ocidPart.startsWith("(OCID:")) {
            String ocid = ocidPart.substring(6).trim();
            String[] subElements = ocid.split("\\)");
            if (subElements.length > 0) {
              bake.setAmi(subElements[0]);
            }
          }
        }
      }
    }
    return bake;
  }

  private ManagedOracleAccount resolveAccount(BakeRequest bakeRequest) {
    ManagedOracleAccount managedAccount = null;

    if (StringUtils.isNullOrEmpty(bakeRequest.getAccount_name())) {
      if (oracleConfigurationProperties != null &&
              oracleConfigurationProperties.getAccounts() != null &&
              oracleConfigurationProperties.getAccounts().size() > 0) {
        managedAccount = oracleConfigurationProperties.getAccounts().get(0);
      }
    } else {
      if (oracleConfigurationProperties != null &&
              oracleConfigurationProperties.getAccounts() != null &&
              oracleConfigurationProperties.getAccounts().size() > 0) {
        managedAccount = oracleConfigurationProperties.getAccounts().stream()
                .filter(account -> bakeRequest.getAccount_name().equals(account.getName()))
                .findAny()
                .orElse(null);
      }
    }

    if (managedAccount == null) {
      throw new IllegalArgumentException("Could not resolve Oracle account: (account_name=$bakeRequest.account_name).");
    }

    return managedAccount;
  }

}

package com.netflix.spinnaker.rosco.providers.docker

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter

class DockerImageNameFactory extends ImageNameFactory {

  @Override
  def buildAppVersionStr(BakeRequest bakeRequest, List<PackageNameConverter.OsPackageName> osPackageNames, BakeRequest.PackageType packageType) {
    super.buildAppVersionStr(bakeRequest, osPackageNames, packageType) ?: clock.millis().toString()
  }

  @Override
  def buildImageName(BakeRequest bakeRequest, List<PackageNameConverter.OsPackageName> osPackageNames) {

    String imageName = bakeRequest.ami_name ?: osPackageNames.first()?.name
    String imageNamePrefixed = [bakeRequest.organization, imageName].findAll({it}).join("/")

    return imageNamePrefixed
  }

}

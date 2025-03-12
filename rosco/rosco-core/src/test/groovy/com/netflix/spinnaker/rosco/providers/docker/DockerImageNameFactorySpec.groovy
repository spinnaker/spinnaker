package com.netflix.spinnaker.rosco.providers.docker

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Specification

import java.time.Clock

class DockerImageNameFactorySpec extends Specification implements TestDefaults {

  void "Should provide a docker image name based on package_name and organization"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DockerImageNameFactory(clock: clockMock)
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced-0.1-3.all kato redis-server",
        build_number: "12",
        commit_hash: "170cdbd",
        organization: "ECorp",
        base_os: "centos")
      def osPackages = parseRpmOsPackageNames(bakeRequest.package_name)
    when:
      def imageName = imageNameFactory.buildImageName(bakeRequest, osPackages)
      def appVersionStr = imageNameFactory.buildAppVersionStr(bakeRequest, osPackages, RPM_PACKAGE_TYPE)
      def packagesParameter = imageNameFactory.buildPackagesParameter(RPM_PACKAGE_TYPE, osPackages)
    then:
      imageName == "ECorp/nflx-djangobase-enhanced"
      appVersionStr == "nflx-djangobase-enhanced-0.1-h12.170cdbd"
      packagesParameter == "nflx-djangobase-enhanced-0.1-3 kato redis-server"
  }

  void "Should provide a docker image name based on ami_name and no organization"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DockerImageNameFactory(clock: clockMock)
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced-0.1-3.all kato redis-server",
        build_number: "12",
        commit_hash: "170cdbd",
        ami_name: "superimage",
        base_os: "centos")
      def osPackages = parseRpmOsPackageNames(bakeRequest.package_name)
    when:
      def imageName = imageNameFactory.buildImageName(bakeRequest, osPackages)
      def appVersionStr = imageNameFactory.buildAppVersionStr(bakeRequest, osPackages, RPM_PACKAGE_TYPE)
      def packagesParameter = imageNameFactory.buildPackagesParameter(RPM_PACKAGE_TYPE, osPackages)
    then:
      imageName == "superimage"
      appVersionStr == "nflx-djangobase-enhanced-0.1-h12.170cdbd"
      packagesParameter == "nflx-djangobase-enhanced-0.1-3 kato redis-server"
  }

  void "Should provide a docker image name and tag with minimal parameters in the BakeRequest"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DockerImageNameFactory(clock: clockMock)
      def bakeRequest = new BakeRequest(package_name: "kato redis-server",
        base_os: "centos",
        ami_name:  'superimage',
      )
      def osPackages = parseRpmOsPackageNames(bakeRequest.package_name)
    when:
      def imageName = imageNameFactory.buildImageName(bakeRequest, osPackages)
      def appVersionStr = imageNameFactory.buildAppVersionStr(bakeRequest, osPackages, RPM_PACKAGE_TYPE)
      def packagesParameter = imageNameFactory.buildPackagesParameter(RPM_PACKAGE_TYPE, osPackages)
    then:
      clockMock.millis() >> SOME_MILLISECONDS.toLong()
      imageName == "superimage"
      appVersionStr == SOME_MILLISECONDS
      packagesParameter == "kato redis-server"
  }
}

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstanceTypesResult
import com.amazonaws.services.ec2.model.InstanceTypeInfo
import com.amazonaws.services.ec2.model.ProcessorInfo
import com.amazonaws.services.ec2.model.VCpuInfo
import com.amazonaws.services.ec2.model.MemoryInfo
import com.amazonaws.services.ec2.model.InstanceStorageInfo
import com.amazonaws.services.ec2.model.EbsInfo
import com.amazonaws.services.ec2.model.EbsOptimizedInfo
import com.amazonaws.services.ec2.model.NetworkInfo
import com.amazonaws.services.ec2.model.GpuInfo
import com.amazonaws.services.ec2.model.GpuDeviceInfo
import com.amazonaws.services.ec2.model.GpuDeviceMemoryInfo
import com.amazonaws.services.ec2.model.DiskInfo
import com.amazonaws.services.ec2.model.NetworkCardInfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonInstanceTypeCachingAgentSpec extends Specification {
  def region = "us-east-1"
  def objectMapper = new ObjectMapper()
  def amazonClientProvider = Mock(AmazonClientProvider)
  def account = "test"
  def credentials = Stub(NetflixAmazonCredentials) {
    getName() >> account
  }

  @Shared
  ProviderCache providerCache = Mock(ProviderCache)

  @Subject
  def agent = new AmazonInstanceTypeCachingAgent(region, amazonClientProvider, credentials, objectMapper)

  @Shared
  AmazonEC2 ec2

  @Shared
  def it1, it2

  def setup() {
    ec2 = Mock(AmazonEC2)
    it1 = getInstanceTypeWithEbs()
    it2 = getInstanceTypeWithGpu()
  }

  def "should cache ec2 instance types info and metadata"() {
    when:
    def result = agent.loadData(providerCache)
    def cache = result.cacheResults

    then:
    1 * amazonClientProvider.getAmazonEC2(credentials, region) >> ec2
    1 * ec2.describeInstanceTypes(_) >> new DescribeInstanceTypesResult(instanceTypes: [it1, it2])

    and:
    cache.size() == 2
    cache.keySet() == [agent.getAgentType(), Keys.Namespace.INSTANCE_TYPES.getNs()] as Set
    (cache.get(agent.getAgentType())[0] as DefaultCacheData).getId() == "metadata" && cache.get(agent.getAgentType()) != null
    cache.get(Keys.Namespace.INSTANCE_TYPES.getNs()) != null
  }

  def "should cache expected attributes for instance types"() {
    when:
    def result = agent.loadData(providerCache)
    def cache = result.cacheResults

    then:
    1 * amazonClientProvider.getAmazonEC2(credentials, region) >> ec2
    1 * ec2.describeInstanceTypes(_) >> new DescribeInstanceTypesResult(instanceTypes: [it1, it2])

    and:
    def instanceTypesInfo = cache.get(Keys.Namespace.INSTANCE_TYPES.getNs())
    instanceTypesInfo.size() == 2
    def it1Result = instanceTypesInfo.find{ it.attributes.name == "test.large" }
    it1Result != null
    def it2Result = instanceTypesInfo.find{ it.attributes.name == "test.xlarge" }
    it2Result != null
  }

  def "should cache a list of instance types under metadata"() {
    when:
    def result = agent.loadData(providerCache)
    def cache = result.cacheResults

    then:
    1 * amazonClientProvider.getAmazonEC2(credentials, region) >> ec2
    1 * ec2.describeInstanceTypes(_) >> new DescribeInstanceTypesResult(instanceTypes: [it1, it2])

    and:
    def metadata = cache.get(agent.getAgentType())?.head()
    metadata != null && metadata.id == "metadata"
    def cachedInstanceTypes = metadata.attributes.cachedInstanceTypes as Set
    cachedInstanceTypes.size() == 2
    cachedInstanceTypes == ["test.large", "test.xlarge"] as Set
  }

  InstanceTypeInfo getInstanceTypeWithEbs() {
    return new InstanceTypeInfo(
      instanceType: "test.large",
      currentGeneration: false,
      supportedUsageClasses: ["on-demand","spot"],
      supportedRootDeviceTypes: ["ebs","instance-store"],
      supportedVirtualizationTypes: ["hvm","paravirtual"],
      bareMetal: false,
      hypervisor: "xen",
      processorInfo: new ProcessorInfo(supportedArchitectures: ["i386","x86_64"], sustainedClockSpeedInGhz: 2.8),
      vCpuInfo: new VCpuInfo(
        defaultVCpus: 2,
        defaultCores: 1,
        defaultThreadsPerCore: 2,
        validCores: [1],
        validThreadsPerCore: [1, 2]
      ),
      memoryInfo: new MemoryInfo(sizeInMiB: 3840),
      instanceStorageSupported: true,
      instanceStorageInfo: new InstanceStorageInfo(
        totalSizeInGB: 32,
        disks: [new DiskInfo(sizeInGB: 16, count: 2, type: "ssd")],
        nvmeSupport: "unsupported"
      ),
      ebsInfo: new EbsInfo(
        ebsOptimizedSupport: "unsupported",
        encryptionSupport: "supported",
        nvmeSupport: "unsupported"
      ),
      networkInfo: new NetworkInfo(
        ipv6Supported: true,
      ),
      burstablePerformanceSupported: false)
  }

  InstanceTypeInfo getInstanceTypeWithGpu() {
    return new InstanceTypeInfo(
      instanceType: "test.xlarge",
      currentGeneration: true,
      supportedUsageClasses: ["on-demand","spot"],
      supportedRootDeviceTypes: ["ebs"],
      supportedVirtualizationTypes: ["hvm"],
      bareMetal: false,
      hypervisor: "xen",
      processorInfo: new ProcessorInfo(
        supportedArchitectures: ["x86_64"],
        sustainedClockSpeedInGhz: 2.7
      ),
      vCpuInfo: new VCpuInfo(
        defaultVCpus: 32,
        defaultCores: 16,
        defaultThreadsPerCore: 2,
        validCores: [1,2,3],
        validThreadsPerCore: [1,2]
      ),
      memoryInfo: new MemoryInfo(sizeInMiB: 249856),
      instanceStorageSupported: false,
      ebsInfo: new EbsInfo(
        ebsOptimizedSupport: "default",
        encryptionSupport: "supported",
        ebsOptimizedInfo: new EbsOptimizedInfo(
          baselineBandwidthInMbps: 7000,
          baselineThroughputInMBps: 875.0,
          baselineIops: 40000,
          maximumBandwidthInMbps: 7000,
          maximumThroughputInMBps: 875.0,
          maximumIops: 40000
        ),
        nvmeSupport: "unsupported"
      ),
      networkInfo: new NetworkInfo(
        ipv6Supported: true,
      ),
      gpuInfo: new GpuInfo(
        gpus: [
          new GpuDeviceInfo(
            name: "V100",
            manufacturer: "NVIDIA",
            count: 4,
            memoryInfo: new GpuDeviceMemoryInfo(sizeInMiB: 16384))
        ],
        totalGpuMemoryInMiB: 65536
      ),
      burstablePerformanceSupported: false,
    )
  }
}

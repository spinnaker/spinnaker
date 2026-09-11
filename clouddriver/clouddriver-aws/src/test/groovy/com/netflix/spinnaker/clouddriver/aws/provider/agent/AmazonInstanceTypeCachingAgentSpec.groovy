package com.netflix.spinnaker.clouddriver.aws.provider.agent

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesResponse
import software.amazon.awssdk.services.ec2.model.InstanceTypeInfo
import software.amazon.awssdk.services.ec2.model.ProcessorInfo
import software.amazon.awssdk.services.ec2.model.VCpuInfo
import software.amazon.awssdk.services.ec2.model.MemoryInfo
import software.amazon.awssdk.services.ec2.model.InstanceStorageInfo
import software.amazon.awssdk.services.ec2.model.EbsInfo
import software.amazon.awssdk.services.ec2.model.EbsOptimizedInfo
import software.amazon.awssdk.services.ec2.model.NetworkInfo
import software.amazon.awssdk.services.ec2.model.GpuInfo
import software.amazon.awssdk.services.ec2.model.GpuDeviceInfo
import software.amazon.awssdk.services.ec2.model.GpuDeviceMemoryInfo
import software.amazon.awssdk.services.ec2.model.DiskInfo
import software.amazon.awssdk.services.ec2.model.NetworkCardInfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonInstanceTypeCachingAgentSpec extends Specification {
  def region = "us-east-1"
  def objectMapper = AmazonObjectMapperConfigurer.createConfigured().registerModule(new AwsSdkV2Module())
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
  Ec2Client ec2

  @Shared
  def it1, it2

  def setup() {
    ec2 = Mock(Ec2Client)
    it1 = getInstanceTypeWithEbs()
    it2 = getInstanceTypeWithGpu()
  }

  def "should cache ec2 instance types info and metadata"() {
    when:
    def result = agent.loadData(providerCache)
    def cache = result.cacheResults

    then:
    1 * amazonClientProvider.getAmazonEC2V2(credentials, region) >> ec2
    1 * ec2.describeInstanceTypes(_) >> DescribeInstanceTypesResponse.builder().instanceTypes([it1, it2]).build()

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
    1 * amazonClientProvider.getAmazonEC2V2(credentials, region) >> ec2
    1 * ec2.describeInstanceTypes(_) >> DescribeInstanceTypesResponse.builder().instanceTypes([it1, it2]).build()

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
    1 * amazonClientProvider.getAmazonEC2V2(credentials, region) >> ec2
    1 * ec2.describeInstanceTypes(_) >> DescribeInstanceTypesResponse.builder().instanceTypes([it1, it2]).build()

    and:
    def metadata = cache.get(agent.getAgentType())?.head()
    metadata != null && metadata.id == "metadata"
    def cachedInstanceTypes = metadata.attributes.cachedInstanceTypes as Set
    cachedInstanceTypes.size() == 2
    cachedInstanceTypes == ["test.large", "test.xlarge"] as Set
  }

  InstanceTypeInfo getInstanceTypeWithEbs() {
    return InstanceTypeInfo.builder()
      .instanceType("test.large")
      .currentGeneration(false)
      .supportedUsageClassesWithStrings("on-demand", "spot")
      .supportedRootDeviceTypesWithStrings("ebs", "instance-store")
      .supportedVirtualizationTypesWithStrings("hvm", "paravirtual")
      .bareMetal(false)
      .hypervisor("xen")
      .processorInfo(ProcessorInfo.builder().supportedArchitecturesWithStrings("i386", "x86_64").sustainedClockSpeedInGhz(2.8).build())
      .vCpuInfo(VCpuInfo.builder()
        .defaultVCpus(2)
        .defaultCores(1)
        .defaultThreadsPerCore(2)
        .validCores(1)
        .validThreadsPerCore(1, 2)
        .build())
      .memoryInfo(MemoryInfo.builder().sizeInMiB(3840).build())
      .instanceStorageSupported(true)
      .instanceStorageInfo(InstanceStorageInfo.builder()
        .totalSizeInGB(32)
        .disks(DiskInfo.builder().sizeInGB(16).count(2).type("ssd").build())
        .nvmeSupport("unsupported")
        .build())
      .ebsInfo(EbsInfo.builder()
        .ebsOptimizedSupport("unsupported")
        .encryptionSupport("supported")
        .nvmeSupport("unsupported")
        .build())
      .networkInfo(NetworkInfo.builder().ipv6Supported(true).build())
      .burstablePerformanceSupported(false)
      .build()
  }

  InstanceTypeInfo getInstanceTypeWithGpu() {
    return InstanceTypeInfo.builder()
      .instanceType("test.xlarge")
      .currentGeneration(true)
      .supportedUsageClassesWithStrings("on-demand", "spot")
      .supportedRootDeviceTypesWithStrings("ebs")
      .supportedVirtualizationTypesWithStrings("hvm")
      .bareMetal(false)
      .hypervisor("xen")
      .processorInfo(ProcessorInfo.builder()
        .supportedArchitecturesWithStrings("x86_64")
        .sustainedClockSpeedInGhz(2.7)
        .build())
      .vCpuInfo(VCpuInfo.builder()
        .defaultVCpus(32)
        .defaultCores(16)
        .defaultThreadsPerCore(2)
        .validCores(1, 2, 3)
        .validThreadsPerCore(1, 2)
        .build())
      .memoryInfo(MemoryInfo.builder().sizeInMiB(249856).build())
      .instanceStorageSupported(false)
      .ebsInfo(EbsInfo.builder()
        .ebsOptimizedSupport("default")
        .encryptionSupport("supported")
        .ebsOptimizedInfo(EbsOptimizedInfo.builder()
          .baselineBandwidthInMbps(7000)
          .baselineThroughputInMBps(875.0)
          .baselineIops(40000)
          .maximumBandwidthInMbps(7000)
          .maximumThroughputInMBps(875.0)
          .maximumIops(40000)
          .build())
        .nvmeSupport("unsupported")
        .build())
      .networkInfo(NetworkInfo.builder().ipv6Supported(true).build())
      .gpuInfo(GpuInfo.builder()
        .gpus(GpuDeviceInfo.builder()
          .name("V100")
          .manufacturer("NVIDIA")
          .count(4)
          .memoryInfo(GpuDeviceMemoryInfo.builder().sizeInMiB(16384).build())
          .build())
        .totalGpuMemoryInMiB(65536)
        .build())
      .burstablePerformanceSupported(false)
      .build()
  }
}

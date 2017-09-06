package com.netflix.spinnaker.front50.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import com.netflix.spinnaker.clouddriver.aws.bastion.BastionConfig;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.front50.model.EventingS3ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.S3StorageService;
import com.netflix.spinnaker.front50.model.TemporarySQSQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

@Configuration
@ConditionalOnExpression("${spinnaker.s3.enabled:false}")
@Import(BastionConfig.class)
@EnableConfigurationProperties(S3Properties.class)
public class S3Config extends CommonStorageServiceDAOConfig {
  @Bean
  public AmazonClientProvider amazonClientProvider() {
    return new AmazonClientProvider();
  }

  @Bean
  public AmazonS3 awsS3Client(AWSCredentialsProvider awsCredentialsProvider, S3Properties s3Properties) {
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    if (s3Properties.getProxyProtocol() != null) {
      if (s3Properties.getProxyProtocol().equalsIgnoreCase("HTTPS")) {
        clientConfiguration.setProtocol(Protocol.HTTPS);
      } else {
        clientConfiguration.setProtocol(Protocol.HTTP);
      }
      Optional.ofNullable(s3Properties.getProxyHost())
        .ifPresent(clientConfiguration::setProxyHost);
      Optional.ofNullable(s3Properties.getProxyPort())
        .map(Integer::parseInt)
        .ifPresent(clientConfiguration::setProxyPort);
    }

    AmazonS3Client client = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);

    if (s3Properties.getEndpoint() != null) {
      client.setEndpoint(s3Properties.getEndpoint());
      client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build());
    } else {
      Optional.ofNullable(s3Properties.getRegion())
        .map(Regions::fromName)
        .map(Region::getRegion)
        .ifPresent(client::setRegion);
    }

    return client;
  }

  @Bean
  public AmazonSQS awsSQSClient(AWSCredentialsProvider awsCredentialsProvider, S3Properties s3Properties) {
    return AmazonSQSClientBuilder
      .standard()
      .withCredentials(awsCredentialsProvider)
      .withClientConfiguration(new ClientConfiguration())
      .withRegion(s3Properties.getRegion())
      .build();
  }

  @Bean
  public AmazonSNS awsSNSClient(AWSCredentialsProvider awsCredentialsProvider, S3Properties s3Properties) {
    return AmazonSNSClientBuilder
      .standard()
      .withCredentials(awsCredentialsProvider)
      .withClientConfiguration(new ClientConfiguration())
      .withRegion(s3Properties.getRegion())
      .build();
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  @ConditionalOnExpression("${spinnaker.s3.eventing.enabled:false}")
  public TemporarySQSQueue temporaryQueueSupport(Optional<ApplicationInfoManager> applicationInfoManager,
                                                 AmazonSQS amazonSQS,
                                                 AmazonSNS amazonSNS,
                                                 S3Properties s3Properties) {
    return new TemporarySQSQueue(
      amazonSQS,
      amazonSNS,
      s3Properties.eventing.getSnsTopicName(),
      getInstanceId(applicationInfoManager)
    );
  }

  @Bean
  @ConditionalOnExpression("${spinnaker.s3.eventing.enabled:false}")
  public ObjectKeyLoader eventingS3ObjectKeyLoader(TaskScheduler taskScheduler,
                                                   ObjectMapper objectMapper,
                                                   S3Properties s3Properties,
                                                   S3StorageService s3StorageService,
                                                   TemporarySQSQueue temporaryQueueSupport) {
    return new EventingS3ObjectKeyLoader(
      taskScheduler,
      objectMapper,
      s3Properties,
      temporaryQueueSupport,
      s3StorageService,
      true
    );
  }

  @Bean
  public S3StorageService s3StorageService(AmazonS3 amazonS3,
                                           S3Properties s3Properties) {
    ObjectMapper awsObjectMapper = new ObjectMapper();
    AmazonObjectMapperConfigurer.configure(awsObjectMapper);

    S3StorageService service = new S3StorageService(
      awsObjectMapper,
      amazonS3,
      s3Properties.getBucket(),
      s3Properties.getRootFolder(),
      s3Properties.isFailoverEnabled(),
      s3Properties.getRegion(),
      s3Properties.getVersioning()
    );
    service.ensureBucketExists();

    return service;
  }

  /**
   * This will likely need improvement should it ever need to run in a non-eureka environment.
   *
   * @return instance identifier that will be used to create a uniquely named sqs queue
   */
  private static String getInstanceId(Optional<ApplicationInfoManager> applicationInfoManager) {
    if (applicationInfoManager.isPresent()) {
      return applicationInfoManager.get().getInfo().getInstanceId();
    }

    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }
}

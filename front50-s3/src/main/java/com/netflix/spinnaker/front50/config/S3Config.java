package com.netflix.spinnaker.front50.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.*;
import com.netflix.spinnaker.front50.plugins.PluginBinaryStorageService;
import com.netflix.spinnaker.front50.plugins.S3PluginBinaryStorageService;
import com.netflix.spinnaker.kork.aws.bastion.BastionConfig;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty("spinnaker.s3.enabled")
@Import(BastionConfig.class)
@EnableConfigurationProperties({S3MetadataStorageProperties.class, S3PluginStorageProperties.class})
public class S3Config extends CommonStorageServiceDAOConfig {

  @Bean
  @ConditionalOnProperty(value = "spinnaker.s3.storage-service.enabled", matchIfMissing = true)
  public AmazonS3 awsS3MetadataClient(
      AWSCredentialsProvider awsCredentialsProvider, S3MetadataStorageProperties s3Properties) {
    return S3ClientFactory.create(awsCredentialsProvider, s3Properties);
  }

  @Bean
  @ConditionalOnProperty(value = "spinnaker.s3.storage-service.enabled", matchIfMissing = true)
  public S3StorageService s3StorageService(
      AmazonS3 awsS3MetadataClient, S3MetadataStorageProperties s3Properties) {
    ObjectMapper awsObjectMapper = new ObjectMapper();

    S3StorageService service =
        new S3StorageService(
            awsObjectMapper,
            awsS3MetadataClient,
            s3Properties.getBucket(),
            s3Properties.getRootFolder(),
            s3Properties.isFailoverEnabled(),
            s3Properties.getRegion(),
            s3Properties.getVersioning(),
            s3Properties.getMaxKeys(),
            s3Properties.getServerSideEncryption());
    service.ensureBucketExists();

    return service;
  }

  @Bean
  @ConditionalOnProperty("spinnaker.s3.plugin-storage.enabled")
  public AmazonS3 awsS3PluginClient(
      AWSCredentialsProvider awsCredentialsProvider, S3PluginStorageProperties s3Properties) {
    return S3ClientFactory.create(awsCredentialsProvider, s3Properties);
  }

  @Bean
  @ConditionalOnProperty("spinnaker.s3.plugin-storage.enabled")
  PluginBinaryStorageService pluginBinaryStorageService(
      AmazonS3 awsS3PluginClient, S3PluginStorageProperties properties) {
    return new S3PluginBinaryStorageService(awsS3PluginClient, properties);
  }

  @Bean
  @ConditionalOnProperty("spinnaker.s3.eventing.enabled")
  public AmazonSQS awsSQSClient(
      AWSCredentialsProvider awsCredentialsProvider, S3MetadataStorageProperties s3Properties) {
    return AmazonSQSClientBuilder.standard()
        .withCredentials(awsCredentialsProvider)
        .withClientConfiguration(new ClientConfiguration())
        .withRegion(s3Properties.getRegion())
        .build();
  }

  @Bean
  @ConditionalOnProperty("spinnaker.s3.eventing.enabled")
  public AmazonSNS awsSNSClient(
      AWSCredentialsProvider awsCredentialsProvider, S3MetadataStorageProperties s3Properties) {
    return AmazonSNSClientBuilder.standard()
        .withCredentials(awsCredentialsProvider)
        .withClientConfiguration(new ClientConfiguration())
        .withRegion(s3Properties.getRegion())
        .build();
  }

  @Bean
  @ConditionalOnProperty("spinnaker.s3.eventing.enabled")
  public TemporarySQSQueue temporaryQueueSupport(
      Optional<ApplicationInfoManager> applicationInfoManager,
      AmazonSQS amazonSQS,
      AmazonSNS amazonSNS,
      S3MetadataStorageProperties s3Properties) {
    return new TemporarySQSQueue(
        amazonSQS,
        amazonSNS,
        s3Properties.getEventing().getSnsTopicName(),
        getInstanceId(applicationInfoManager));
  }

  @Bean
  @ConditionalOnProperty("spinnaker.s3.eventing.enabled")
  public ObjectKeyLoader eventingS3ObjectKeyLoader(
      ObjectMapper objectMapper,
      S3MetadataStorageProperties s3Properties,
      StorageService storageService,
      TemporarySQSQueue temporaryQueueSupport,
      Registry registry) {
    return new EventingS3ObjectKeyLoader(
        Executors.newFixedThreadPool(1),
        objectMapper,
        s3Properties,
        temporaryQueueSupport,
        storageService,
        registry,
        true);
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

package com.netflix.spinnaker.front50.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.bastion.BastionConfig;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.front50.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;
import rx.schedulers.Schedulers;

import java.util.Optional;
import java.util.concurrent.Executors;

@Configuration
@ConditionalOnExpression("${spinnaker.s3.enabled:false}")
@Import(BastionConfig.class)
public class S3Config {

  @Value("${spinnaker.s3.bucket}")
  private String bucket;

  @Value("${spinnaker.s3.rootFolder}")
  private String rootFolder;

  @Value("${spinnaker.s3.region:#{null}}")
  private String s3Region;

  @Value("${spinnaker.s3.proxyHost:#{null}}")
  private String proxyHost;

  @Value("${spinnaker.s3.proxyPort:#{null}}")
  private String proxyPort;

  @Value("${spinnaker.s3.proxyProtocol:#{null}}")
  private String proxyProtocol;

  @Bean
  public AmazonClientProvider amazonClientProvider() {
    return new AmazonClientProvider();
  }

  @Bean
  public AmazonS3 awsS3Client(AWSCredentialsProvider awsCredentialsProvider) {
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    if (proxyProtocol != null) {
      if (proxyProtocol.equalsIgnoreCase("HTTPS")) {
        clientConfiguration.setProtocol(Protocol.HTTPS);
      } else {
        clientConfiguration.setProtocol(Protocol.HTTP);
      }
      Optional.ofNullable(proxyHost)
              .ifPresent(clientConfiguration::setProxyHost);
      Optional.ofNullable(proxyPort)
              .map(Integer::parseInt)
              .ifPresent(clientConfiguration::setProxyPort);
    }

    AmazonS3Client client = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);
    Optional.ofNullable(s3Region)
            .map(Regions::fromName)
            .map(Region::getRegion)
            .ifPresent(client::setRegion);
    return client;
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public ObjectMapper amazonObjectMapper() {
    return new AmazonObjectMapper();
  }

  @Bean
  public S3ApplicationDAO s3ApplicationDAO(ObjectMapper objectMapper, AmazonS3 amazonS3) {
    return new S3ApplicationDAO(objectMapper, amazonS3, Schedulers.from(Executors.newFixedThreadPool(20)), 15000, bucket, rootFolder);
  }

  @Bean
  public S3ApplicationPermissionDAO s3ApplicationPermissionDAO(ObjectMapper objectMapper, AmazonS3 amazonS3) {
    return new S3ApplicationPermissionDAO(objectMapper, amazonS3, Schedulers.from(Executors.newFixedThreadPool(20)), 15000, bucket, rootFolder);
  }

  @Bean
  public S3ServiceAccountDAO s3ServiceAccountDAO(ObjectMapper objectMapper, AmazonS3 amazonS3) {
    return new S3ServiceAccountDAO(objectMapper, amazonS3, Schedulers.from(Executors.newFixedThreadPool(5)), 30000, bucket, rootFolder);
  }

  @Bean
  public S3ProjectDAO s3ProjectDAO(ObjectMapper objectMapper, AmazonS3 amazonS3) {
    return new S3ProjectDAO(objectMapper, amazonS3, Schedulers.from(Executors.newFixedThreadPool(10)), 30000, bucket, rootFolder);
  }

  @Bean
  public S3NotificationDAO s3NotificationDAO(ObjectMapper objectMapper, AmazonS3 amazonS3) {
    return new S3NotificationDAO(objectMapper, amazonS3, Schedulers.from(Executors.newFixedThreadPool(5)), 30000, bucket, rootFolder);
  }

  @Bean
  public S3PipelineStrategyDAO s3PipelineStrategyDAO(ObjectMapper objectMapper, AmazonS3 amazonS3) {
    return new S3PipelineStrategyDAO(objectMapper, amazonS3, Schedulers.from(Executors.newFixedThreadPool(5)), 20000, bucket, rootFolder);
  }

  @Bean
  public S3PipelineDAO s3PipelineDAO(ObjectMapper objectMapper, AmazonS3 amazonS3) {
    return new S3PipelineDAO(objectMapper, amazonS3, Schedulers.from(Executors.newFixedThreadPool(25)), 10000, bucket, rootFolder);
  }

  @Bean
  public S3SnapshotDAO s3SnapshotDAO(ObjectMapper objectMapper, AmazonS3 amazonS3) {
    return new S3SnapshotDAO(objectMapper, amazonS3, Schedulers.from(Executors.newFixedThreadPool(5)), 10000, bucket, rootFolder);
  }
}

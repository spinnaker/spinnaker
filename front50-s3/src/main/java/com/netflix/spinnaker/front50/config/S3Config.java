package com.netflix.spinnaker.front50.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import com.netflix.spinnaker.clouddriver.aws.bastion.BastionConfig;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.front50.model.S3StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;

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
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public S3StorageService s3StorageService(AmazonS3 amazonS3, S3Properties s3Properties) {
    ObjectMapper awsObjectMapper = new ObjectMapper();
    AmazonObjectMapperConfigurer.configure(awsObjectMapper);

    S3StorageService service = new S3StorageService(awsObjectMapper, amazonS3, s3Properties.getBucket(), s3Properties.getRootFolder(), s3Properties.isFailoverEnabled(), s3Properties.getRegion());
    service.ensureBucketExists();
    return service;
  }
}

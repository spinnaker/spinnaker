package com.netflix.spinnaker.front50.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.netflix.spinnaker.kork.aws.bastion.BastionConfig;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty("spinnaker.s3.enabled")
@Import({
  BastionConfig.class,
  S3StorageServiceConfiguration.class,
  S3StorageServiceConfiguration.class
})
@EnableConfigurationProperties(S3Properties.class)
public class S3Config extends CommonStorageServiceDAOConfig {

  @Bean
  public AmazonS3 awsS3Client(
      AWSCredentialsProvider awsCredentialsProvider, S3Properties s3Properties) {
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    if (s3Properties.getProxyProtocol() != null) {
      if (s3Properties.getProxyProtocol().equalsIgnoreCase("HTTPS")) {
        clientConfiguration.setProtocol(Protocol.HTTPS);
      } else {
        clientConfiguration.setProtocol(Protocol.HTTP);
      }
      Optional.ofNullable(s3Properties.getProxyHost()).ifPresent(clientConfiguration::setProxyHost);
      Optional.ofNullable(s3Properties.getProxyPort())
          .map(Integer::parseInt)
          .ifPresent(clientConfiguration::setProxyPort);
    }

    AmazonS3Client client = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);

    if (!StringUtils.isEmpty(s3Properties.getEndpoint())) {
      client.setEndpoint(s3Properties.getEndpoint());

      if (!StringUtils.isEmpty(s3Properties.getRegionOverride())) {
        client.setSignerRegionOverride(s3Properties.getRegionOverride());
      }

      client.setS3ClientOptions(
          S3ClientOptions.builder().setPathStyleAccess(s3Properties.getPathStyleAccess()).build());
    } else {
      Optional.ofNullable(s3Properties.getRegion())
          .map(Regions::fromName)
          .map(Region::getRegion)
          .ifPresent(client::setRegion);
    }

    return client;
  }
}

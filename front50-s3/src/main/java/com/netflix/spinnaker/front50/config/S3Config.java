package com.netflix.spinnaker.front50.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import com.netflix.spinnaker.clouddriver.aws.bastion.BastionConfig;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.front50.model.S3StorageService;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.notification.DefaultNotificationDAO;
import com.netflix.spinnaker.front50.model.notification.NotificationDAO;
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineStrategyDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO;
import com.netflix.spinnaker.front50.model.project.DefaultProjectDAO;
import com.netflix.spinnaker.front50.model.project.ProjectDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.DefaultServiceAccountDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.front50.model.snapshot.DefaultSnapshotDAO;
import com.netflix.spinnaker.front50.model.snapshot.SnapshotDAO;
import com.netflix.spinnaker.front50.model.tag.DefaultEntityTagsDAO;
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO;
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

  @Value("${spinnaker.s3.failover.enabled:false}")
  private Boolean failoverEnabled;

  @Value("${spinnaker.s3.failover.bucket:#{null}}")
  private String failoverBucket;

  @Value("${spinnaker.s3.failover.proxyHost:#{null}}")
  private String failoverProxyHost;

  @Value("${spinnaker.s3.failover.proxyPort:#{null}}")
  private String failoverProxyPort;

  @Value("${spinnaker.s3.failover.proxyProtocol:#{null}}")
  private String failoverProxyProtocol;

  @Value("${spinnaker.s3.failover.region:#{null}}")
  private String failoverS3Region;

  @Bean
  public AmazonClientProvider amazonClientProvider() {
    return new AmazonClientProvider();
  }

  @Bean
  public AmazonS3 awsS3Client(AWSCredentialsProvider awsCredentialsProvider) {
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    if (getProxyProtocol() != null) {
      if (getProxyProtocol().equalsIgnoreCase("HTTPS")) {
        clientConfiguration.setProtocol(Protocol.HTTPS);
      } else {
        clientConfiguration.setProtocol(Protocol.HTTP);
      }
      Optional.ofNullable(getProxyHost())
        .ifPresent(clientConfiguration::setProxyHost);
      Optional.ofNullable(getProxyPort())
        .map(Integer::parseInt)
        .ifPresent(clientConfiguration::setProxyPort);
    }

    AmazonS3Client client = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);
    Optional.ofNullable(getS3Region())
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
  public S3StorageService s3StorageService(AmazonS3 amazonS3) {
    ObjectMapper awsObjectMapper = new ObjectMapper();
    AmazonObjectMapperConfigurer.configure(awsObjectMapper);

    return new S3StorageService(awsObjectMapper, amazonS3, getBucket(), rootFolder, failoverEnabled);
  }

  @Bean
  public ApplicationDAO applicationDAO(StorageService storageService) {
    return new DefaultApplicationDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 15000);
  }

  @Bean
  public ApplicationPermissionDAO applicationPermissionDAO(StorageService storageService) {
    return new DefaultApplicationPermissionDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 45000);
  }

  @Bean
  public ServiceAccountDAO serviceAccountDAO(StorageService storageService) {
    return new DefaultServiceAccountDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 30000);
  }

  @Bean
  public ProjectDAO projectDAO(StorageService storageService) {
    return new DefaultProjectDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 30000);
  }

  @Bean
  public NotificationDAO notificationDAO(StorageService storageService) {
    return new DefaultNotificationDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 30000);
  }

  @Bean
  public PipelineStrategyDAO pipelineStrategyDAO(StorageService storageService) {
    return new DefaultPipelineStrategyDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 20000);
  }

  @Bean
  public PipelineDAO pipelineDAO(StorageService storageService) {
    return new DefaultPipelineDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(25)), 10000);
  }

  @Bean
  public SnapshotDAO snapshotDAO(StorageService storageService) {
    return new DefaultSnapshotDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 60000);
  }

  @Bean
  public EntityTagsDAO entityTagsDAO(StorageService storageService) {
    return new DefaultEntityTagsDAO(storageService, null, -1);
  }

  private String getProxyProtocol() {
    if (failoverEnabled) {
      return failoverProxyProtocol;
    }
    return proxyProtocol;
  }

  private String getProxyHost() {
    if (failoverEnabled) {
      return failoverProxyHost;
    }
    return proxyHost;
  }

  private String getProxyPort() {
    if (failoverEnabled) {
      return failoverProxyPort;
    }
    return proxyPort;
  }

  private String getS3Region() {
    if (failoverEnabled) {
      return failoverS3Region;
    }
    return s3Region;
  }

  private String getBucket() {
    if (failoverEnabled) {
      return failoverBucket;
    }
    return bucket;
  }
}

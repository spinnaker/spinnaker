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
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import com.netflix.spinnaker.clouddriver.aws.bastion.BastionConfig;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.front50.model.*;
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
  public S3StorageService s3StorageService(AmazonS3 amazonS3) {
    ObjectMapper awsObjectMapper = new ObjectMapper();
    AmazonObjectMapperConfigurer.configure(awsObjectMapper);

    return new S3StorageService(awsObjectMapper, amazonS3, bucket, rootFolder);
  }

  @Bean
  public ApplicationDAO applicationDAO(StorageService storageService) {
    return new DefaultApplicationDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 15000);
  }

  @Bean
  public ApplicationPermissionDAO applicationPermissionDAO(StorageService storageService) {
    return new DefaultApplicationPermissionDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(1)), 45000);
  }

  @Bean
  public ServiceAccountDAO serviceAccountDAO(StorageService storageService) {
    return new DefaultServiceAccountDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(1)), 30000);
  }

  @Bean
  public ProjectDAO projectDAO(StorageService storageService) {
    return new DefaultProjectDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(2)), 30000);
  }

  @Bean
  public NotificationDAO notificationDAO(StorageService storageService) {
    return new DefaultNotificationDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(5)), 30000);
  }

  @Bean
  public PipelineStrategyDAO pipelineStrategyDAO(StorageService storageService) {
    return new DefaultPipelineStrategyDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(5)), 20000);
  }

  @Bean
  public PipelineDAO pipelineDAO(StorageService storageService) {
    return new DefaultPipelineDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(25)), 10000);
  }

  @Bean
  public SnapshotDAO snapshotDAO(StorageService storageService) {
    return new DefaultSnapshotDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(1)), 60000);
  }

  @Bean
  public EntityTagsDAO entityTagsDAO(StorageService storageService) {
    return new DefaultEntityTagsDAO(storageService, null, -1);
  }
}

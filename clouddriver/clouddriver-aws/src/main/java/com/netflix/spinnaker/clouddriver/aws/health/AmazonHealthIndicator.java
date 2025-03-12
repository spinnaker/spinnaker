package com.netflix.spinnaker.clouddriver.aws.health;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.core.AccountHealthIndicator;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmazonHealthIndicator extends AccountHealthIndicator<NetflixAmazonCredentials> {

  private static final String ID = "aws";
  private final CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;
  private final AmazonClientProvider amazonClientProvider;
  private final AwsConfigurationProperties awsConfigurationProperties;

  public AmazonHealthIndicator(
      Registry registry,
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
      AmazonClientProvider amazonClientProvider,
      AwsConfigurationProperties awsConfigurationProperties) {
    super(ID, registry);
    this.credentialsRepository = credentialsRepository;
    this.amazonClientProvider = amazonClientProvider;
    this.awsConfigurationProperties = awsConfigurationProperties;
  }

  @Override
  protected Iterable<? extends NetflixAmazonCredentials> getAccounts() {
    return credentialsRepository.getAll();
  }

  @Override
  protected Optional<String> accountHealth(NetflixAmazonCredentials account) {
    if (awsConfigurationProperties.getHealth().getVerifyAccountHealth()) {
      log.info(
          "aws.health.verifyAccountHealth flag is enabled - verifying connection to the EC2 accounts");
      try {
        AmazonEC2 ec2 =
            amazonClientProvider.getAmazonEC2(account, AmazonClientProvider.DEFAULT_REGION, true);
        if (ec2 == null) {
          return Optional.of(
              String.format("Could not create Amazon client for '%s'", account.getName()));
        }

        ec2.describeAccountAttributes();

      } catch (AmazonServiceException e) {
        String errorCode = e.getErrorCode();

        if (!"RequestLimitExceeded".equalsIgnoreCase(errorCode)) {
          return Optional.of(
              String.format(
                  "Failed to describe account attributes for '%s'. Message: '%s'",
                  account.getName(), e.getMessage()));
        }
      }
    } else {
      log.info(
          "aws.health.verifyAccountHealth flag is disabled - not verifying connection to the EC2 accounts");
    }

    return Optional.empty();
  }
}

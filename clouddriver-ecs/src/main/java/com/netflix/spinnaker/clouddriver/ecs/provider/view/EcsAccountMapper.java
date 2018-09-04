package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@DependsOn("netflixECSCredentials")
public class EcsAccountMapper {

  final AccountCredentialsProvider accountCredentialsProvider;
  final Map<String, NetflixAssumeRoleEcsCredentials> ecsCredentialsMap;
  final Map<String, NetflixAmazonCredentials> awsCredentialsMap;

  @Autowired
  public EcsAccountMapper(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;

    Set<? extends AccountCredentials> allAccounts = accountCredentialsProvider.getAll();

    Collection<NetflixAssumeRoleEcsCredentials> ecsAccounts =
      (Collection<NetflixAssumeRoleEcsCredentials>) allAccounts
        .stream()
        .filter(credentials -> credentials instanceof NetflixAssumeRoleEcsCredentials)
        .collect(Collectors.toSet());

    ecsCredentialsMap = new HashMap<>();
    awsCredentialsMap = new HashMap<>();

    for (NetflixAssumeRoleEcsCredentials ecsAccount : ecsAccounts) {
      ecsCredentialsMap.put(ecsAccount.getAwsAccount(), ecsAccount);

      allAccounts
        .stream()
        .filter(credentials -> credentials.getName().equals(ecsAccount.getAwsAccount()))
        .findFirst()
        .ifPresent(v -> awsCredentialsMap.put(ecsAccount.getName(), (NetflixAmazonCredentials) v));
    }
  }

  public NetflixECSCredentials fromAwsAccountNameToEcs(String awsAccoutName) {
    return ecsCredentialsMap.get(awsAccoutName);
  }

  public NetflixAmazonCredentials fromEcsAccountNameToAws(String ecsAccountName) {
    return awsCredentialsMap.get(ecsAccountName);
  }
}

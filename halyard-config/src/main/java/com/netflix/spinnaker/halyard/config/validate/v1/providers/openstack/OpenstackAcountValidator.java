package com.netflix.spinnaker.halyard.config.validate.v1.providers.openstack;

import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties;
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.openstack.OpenstackAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.util.ValidatingFileReader;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OpenstackAcountValidator extends Validator<OpenstackAccount> {
  @Override
  public void validate(ConfigProblemSetBuilder psBuilder, OpenstackAccount account) {
    String authUrl = account.getAuthUrl();
    String username = account.getUsername();
    String password = account.getPassword();
    String projectName = account.getPassword();
    String domainName = account.getDomainName();
    Boolean insecure = account.getInsecure();
    String userDataFile = account.getUserDataFile();
    OpenstackAccount.OpenstackLbaasOptions lbaas = account.getLbaas();
    String regions = account.getRegions();

    if (password == null || password.isEmpty() || username == null || username.isEmpty()) {
      psBuilder.addProblem(Problem.Severity.ERROR, "You must provide a both a username and a password");
    }

    if (!StringUtils.endsWith(authUrl, "/v3")) {
      psBuilder.addProblem(Problem.Severity.WARNING, "You must use Keystone v3. The default auth url will be of the format IP:5000/v3.");
    }

    if (lbaas.getPollInterval() < 0) {
      psBuilder.addProblem(Problem.Severity.ERROR, "Poll interval cannot be less than 0.")
          .setRemediation("Update this value to be reasonable. Default is 5.");
    }
    if (lbaas.getPollTimeout() < 0) {
      psBuilder.addProblem(Problem.Severity.ERROR, "Poll timeout cannot be less than 0.")
          .setRemediation("Update this value to be reasonable. Default is 60.");
    }

    if (insecure) {
      psBuilder.addProblem(Problem.Severity.WARNING, "You've chosen to not validate SSL connections. This setup is not recommended in production deployments.");
    }

    boolean userDataProvided = userDataFile != null && !userDataFile.isEmpty();
    if (userDataProvided) {
      String resolvedUserData = ValidatingFileReader.contents(psBuilder, userDataFile);
      if (resolvedUserData == null) {
        return;
      } else if (resolvedUserData.isEmpty()) {
        psBuilder.addProblem(Problem.Severity.WARNING, "The supplied user data file is empty.")
            .setRemediation("Please provide a non empty file, or remove the user data file.");
      }

      List<String> validTokens = Arrays.asList("account", "accounttype", "env", "region", "group", "autogrp", "cluster", "stack", "detail", "launchconfig");
      List<String> tokens = Arrays.asList(StringUtils.substringsBetween(resolvedUserData, "%%", "%%"));
      List<String> invalidTokens = tokens.stream()
          .filter(t -> !validTokens.contains(t))
          .collect(Collectors.toList());
      if (invalidTokens.size() != 0) {
        psBuilder.addProblem(Problem.Severity.WARNING, "The supplied user data file contains tokens that won't be replaced. " +
            "Tokens \"" + StringUtils.join(invalidTokens, ", ") + "\" are not supported.")
            .setRemediation("Please use only the supported tokens \"" + StringUtils.join(validTokens, ", ") + "\".");
      }
    }

    OpenstackConfigurationProperties.LbaasConfig lbaasConfig = new OpenstackConfigurationProperties.LbaasConfig();
    lbaasConfig.setPollInterval(lbaas.getPollInterval());
    lbaasConfig.setPollTimeout(lbaas.getPollTimeout());

    try {
      List<String> regionsList = Arrays.asList(StringUtils.split(regions, ","));
      OpenstackNamedAccountCredentials openstackCredentials = new OpenstackNamedAccountCredentials.Builder()
          .name(account.getName())
          .authUrl(authUrl)
          .username(username)
          .password(password)
          .projectName(projectName)
          .domainName(domainName)
          .regions(regionsList)
          .insecure(insecure)
          .lbaasConfig(lbaasConfig)
          .userDataFile(userDataFile)
          .build();
      //TODO(emjburns) verify that these credentials can connect w/o error to the openstack instance
    } catch (Exception e) {
      psBuilder.addProblem(Problem.Severity.ERROR, "Failed to instantiate openstack credentials for account \"" + account.getName() + "\".");
    }
  }
}

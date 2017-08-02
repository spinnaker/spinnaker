package com.netflix.spinnaker.halyard.config.validate.v1.providers.openstack;

import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig;
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties;
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.openstack.OpenstackAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.util.ValidatingFileReader;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = false)
public class OpenstackAccountValidator extends Validator<OpenstackAccount> {

  final private List<OpenstackNamedAccountCredentials> credentialsList;

  final private String halyardVersion;

  @Override
  public void validate(ConfigProblemSetBuilder psBuilder, OpenstackAccount account) {
    DaemonTaskHandler.message("Validating " + account.getNodeName() + " with " + OpenstackAccountValidator.class.getSimpleName());

    String environment = account.getEnvironment();
    String accountType = account.getAccountType();
    String username = account.getUsername();
    String password = account.getPassword();
    String projectName = account.getPassword();
    String domainName = account.getDomainName();
    String authUrl = account.getAuthUrl();
    List<String> regions = account.getRegions();
    Boolean insecure = account.getInsecure();
    String heatTemplateLocation = account.getHeatTemplateLocation();
    OpenstackAccount.OpenstackLbaasOptions lbaas = account.getLbaas();
    ConsulConfig consulConfig = new ConsulConfig();
    String userDataFile = account.getUserDataFile();

    if (StringUtils.isEmpty(environment)) {
      psBuilder.addProblem(Problem.Severity.ERROR, "You must provide an environment name");
    }

    if (StringUtils.isEmpty(password) || StringUtils.isEmpty(username)) {
      psBuilder.addProblem(Problem.Severity.ERROR, "You must provide a both a username and a password");
    }

    if (StringUtils.isEmpty(projectName)) {
      psBuilder.addProblem(Problem.Severity.ERROR, "You must provide a project name");
    }

    if (!StringUtils.endsWith(authUrl, "/v3")) {
      psBuilder.addProblem(Problem.Severity.WARNING, "You must use Keystone v3. The default auth url will be of the format IP:5000/v3.");
    }

    if (StringUtils.isEmpty(domainName)) {
      psBuilder.addProblem(Problem.Severity.ERROR, "You must provide a domain name");
    }

    if (regions.size() == 0 || StringUtils.isEmpty(regions.get(0))) {
      psBuilder.addProblem(Problem.Severity.ERROR, "You must provide one region");
    }

    if (insecure) {
      psBuilder.addProblem(Problem.Severity.WARNING, "You've chosen to not validate SSL connections. This setup is not recommended in production deployments.");
    }

    if ( heatTemplateLocation != null && heatTemplateLocation.isEmpty()) {
      psBuilder.addProblem(Problem.Severity.ERROR, "Not a valid Heat template location: ''");
    }

    if (lbaas.getPollInterval() < 0) {
      psBuilder.addProblem(Problem.Severity.ERROR, "Poll interval cannot be less than 0.")
          .setRemediation("Update this value to be reasonable. Default is 5.");
    }

    if (lbaas.getPollTimeout() < 0) {
      psBuilder.addProblem(Problem.Severity.ERROR, "Poll timeout cannot be less than 0.")
          .setRemediation("Update this value to be reasonable. Default is 60.");
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
      OpenstackNamedAccountCredentials openstackCredentials = new OpenstackNamedAccountCredentials.Builder()
          .name(account.getName())
          .environment(environment)
          .accountType(accountType)
          .authUrl(authUrl)
          .username(username)
          .password(password)
          .projectName(projectName)
          .domainName(domainName)
          .regions(regions)
          .insecure(insecure)
          .heatTemplateLocation(heatTemplateLocation)
          .consulConfig(consulConfig)
          .lbaasConfig(lbaasConfig)
          .userDataFile(userDataFile)
          .build();
      credentialsList.add(openstackCredentials);
      //TODO(emjburns) verify that these credentials can connect w/o error to the openstack instance
    } catch (Exception e) {
      psBuilder.addProblem(Problem.Severity.ERROR, "Failed to instantiate openstack credentials for account \"" + account.getName() + "\".");
    }
  }
}

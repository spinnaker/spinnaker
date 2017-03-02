package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.openstack;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.PathExpandingConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.openstack.OpenstackAccount;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Parameters()
public class OpenstackAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "openstack";
  }

  @Parameter(
      names = "--auth-url",
      required = true,
      description = OpenstackCommandProperties.AUTH_URL_DESCRIPTION
  )
  private String authUrl;

  @Parameter(
      names = "--username",
      required = true,
      description = OpenstackCommandProperties.USERNAME_DESCRIPTION
  )
  private String username;

  @Parameter(
      names = "--password",
      required = true,
      description = OpenstackCommandProperties.PASSWORD_DESCRIPTION
  )
  private String password;

  @Parameter(
      names = "--project-name",
      required = true,
      description = OpenstackCommandProperties.PROJECT_NAME_DESCRIPTION
  )
  private String projectName;

  @Parameter(
      names = "--domain-name",
      required = true,
      description = OpenstackCommandProperties.DOMAIN_NAME_DESCRIPTION
  )
  private String domainName;

  @Parameter(
      names = "--regions",
      required = true,
      variableArity = true,
      description = OpenstackCommandProperties.REGIONS_DESCRIPTION
  )
  private List<String> regions = new ArrayList<>();

  @Parameter(
      names = "--insecure",
      description = OpenstackCommandProperties.INSECURE_DESCRIPTION
  )
  private boolean insecure;

  @Parameter(
      names = "--user-data-file",
      converter = PathExpandingConverter.class,
      description = OpenstackCommandProperties.USER_DATA_FILE_DESCRIPTION
  )
  private String userDataFile;

  @Parameter(
      names = "--lbaas-poll-timout",
      description = OpenstackCommandProperties.LBAAS_POLL_TIMEOUT_DESCRIPTION
  )
  private Integer lbaasPollTimeout;

  @Parameter(
      names = "--lbaas-poll-interval",
      description = OpenstackCommandProperties.LBAAS_POLL_INTERVAL_DESCRIPTION
  )
  private Integer lbaasPollInterval;

  @Override
  protected Account buildAccount(String accountName) {
    OpenstackAccount account = (OpenstackAccount) new OpenstackAccount().setName(accountName);

    OpenstackAccount.OpenstackLbaasOptions lbaas = new OpenstackAccount.OpenstackLbaasOptions();
    if (isSet(lbaasPollInterval)) {
      lbaas.setPollInterval(lbaasPollInterval);
    }
    if (isSet(lbaasPollTimeout)) {
      lbaas.setPollTimeout(lbaasPollTimeout);
    }

    account.setAuthUrl(authUrl)
        .setUsername(username)
        .setPassword(password)
        .setProjectName(projectName)
        .setDomainName(domainName)
        .setRegions(StringUtils.join(regions, ","))
        .setInsecure(insecure)
        .setUserDataFile(userDataFile)
        .setLbaas(lbaas);

    return account;

  }
}

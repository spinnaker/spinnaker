package com.netflix.spinnaker.halyard.config.validate.v1.providers.ecs;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.ecs.EcsAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.config.services.v1.ConfigService;
import com.netflix.spinnaker.halyard.config.services.v1.ProviderService;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsAccountValidator extends Validator<EcsAccount> {
  @Autowired ProviderService providerService;

  @Autowired AccountService accountService;

  @Autowired ConfigService configService;

  @Override
  public void validate(ConfigProblemSetBuilder p, EcsAccount n) {
    p.addProblem(
        Severity.WARNING,
        "This only validates that a corresponding AWS account has been "
            + "created for your ECS account.");
    String ecsAwsAccount = n.getAwsAccount();

    List<Account> accounts =
        accountService.getAllAccounts(configService.getCurrentDeployment(), "aws");
    Optional<Account> account =
        accounts.stream().filter(act -> act.getName().equals(ecsAwsAccount)).findAny();

    if (!account.isPresent()) {
      p.addProblem(Severity.ERROR, "No AWS Account found matching " + ecsAwsAccount);
    }
  }
}

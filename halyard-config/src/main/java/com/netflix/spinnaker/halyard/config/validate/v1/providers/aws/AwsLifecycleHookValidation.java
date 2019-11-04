package com.netflix.spinnaker.halyard.config.validate.v1.providers.aws;

import com.google.api.client.util.Lists;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider.AwsLifecycleHook;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class AwsLifecycleHookValidation {
  private static final Pattern SNS_PATTERN = Pattern.compile("^arn:aws:sns:[^:]+:[^:]+:[^:]+$");
  private static final Pattern IAM_ROLE_PATTERN = Pattern.compile("^arn:aws:iam::[^:]+:[^:]+$");
  private static final Collection<String> VALID_LIFECYCLE_HOOK_RESULTS =
      Sets.newHashSet("ABANDON", "CONTINUE");

  public static Stream<String> getValidationErrors(AwsLifecycleHook hook) {
    List<String> errors = Lists.newArrayList();
    String snsArn = hook.getNotificationTargetARN();
    if (!isValidSnsArn(snsArn)) {
      errors.add("Invalid SNS notification ARN: " + snsArn);
    }

    if (!isValidRoleArn(hook.getRoleARN())) {
      errors.add("Invalid IAM role ARN: " + hook.getRoleARN());
    }

    if (!VALID_LIFECYCLE_HOOK_RESULTS.contains(hook.getDefaultResult())) {
      errors.add("Invalid lifecycle default result: " + hook.getDefaultResult());
    }

    if (!isValidHeartbeatTimeout(hook.getHeartbeatTimeout())) {
      errors.add(
          "Lifecycle heartbeat timeout must be between 30 and 7200. Provided value was: "
              + hook.getHeartbeatTimeout());
    }

    return errors.stream();
  }

  private static boolean isValidSnsArn(String arn) {
    return SNS_PATTERN.matcher(arn).matches();
  }

  private static boolean isValidRoleArn(String arn) {
    return IAM_ROLE_PATTERN.matcher(arn).matches();
  }

  private static boolean isValidHeartbeatTimeout(Integer timeout) {
    return timeout != null && timeout >= 30 && timeout <= 7200;
  }
}

package com.netflix.spinnaker.halyard.config.validate.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Telemetry;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TelemetryValidator extends Validator<Telemetry> {

  @Override
  public void validate(ConfigProblemSetBuilder p, Telemetry t) {
    StringBuilder msg = new StringBuilder();
    msg.append("Telemetry is currently ");
    if (t.getEnabled()) {
      msg.append("ENABLED. Usage statistics are being collected—Thank you! ");
      msg.append("These stats inform improvements to the product, and that helps the community. ");
      msg.append("To disable, run `hal config telemetry disable`. ");
    } else {
      msg.append("DISABLED. Usage statistics are not being collected. ");
      msg.append("Please consider enabling statistic collection. ");
      msg.append("These stats inform improvements to the product, and that helps the community. ");
      msg.append("To enable, run `hal config telemetry enable`. ");
    }

    msg.append("To learn more about what and how telemetry data is used, please see ");
    msg.append("https://www.spinnaker.io/community/stats.");
    p.addProblem(Severity.INFO, msg.toString());
  }
}

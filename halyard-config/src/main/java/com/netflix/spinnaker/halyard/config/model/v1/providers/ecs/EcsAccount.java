package com.netflix.spinnaker.halyard.config.model.v1.providers.ecs;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class EcsAccount extends Account {

    private String awsAccount;

    @Override
    public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
        v.validate(psBuilder, this);
    }
}

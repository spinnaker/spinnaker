package com.netflix.spinnaker.halyard.config.model.v1.providers.ecs;

import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.ecs.EcsAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class EcsProvider extends Provider<EcsAccount> {
    private String awsAccount;

    @Override
    public ProviderType providerType() {
        return ProviderType.ECS;
    }

    @Override
    public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
        v.validate(psBuilder, this);
    }
}

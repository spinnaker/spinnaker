import { AngularServices, FunctionReader } from '@spinnaker/core';

import { AwsFunctionTransformer } from './function';
import { AwsInstanceTypeService } from './instance/awsInstanceType.service';
import { EvaluateCloudFormationChangeSetExecutionService } from './pipeline/stages/deployCloudFormation/evaluateCloudFormationChangeSetExecution.service';
import { createAwsServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { AwsServerGroupConfigurationService } from './serverGroup/configure/serverGroupConfiguration.service';
import { AwsServerGroupTransformer } from './serverGroup/serverGroup.transformer';

let awsInstanceTypeService: AwsInstanceTypeService;
let awsServerGroupCommandBuilder: ReturnType<typeof createAwsServerGroupCommandBuilder>;
let awsServerGroupConfigurationService: AwsServerGroupConfigurationService;
let awsServerGroupTransformer: AwsServerGroupTransformer;
let functionReader: FunctionReader;
let evaluateCloudFormationChangeSetExecutionService: EvaluateCloudFormationChangeSetExecutionService;

export const AwsServices = {
  get awsInstanceTypeService() {
    return (awsInstanceTypeService = awsInstanceTypeService || new AwsInstanceTypeService());
  },
  get awsServerGroupCommandBuilder() {
    return (awsServerGroupCommandBuilder =
      awsServerGroupCommandBuilder ||
      createAwsServerGroupCommandBuilder(
        AngularServices.instanceTypeService,
        AwsServices.awsServerGroupConfigurationService,
      ));
  },
  get awsServerGroupConfigurationService() {
    return (
      awsServerGroupConfigurationService ||
      (awsServerGroupConfigurationService = new AwsServerGroupConfigurationService())
    );
  },
  get awsServerGroupTransformer() {
    return (awsServerGroupTransformer = awsServerGroupTransformer || new AwsServerGroupTransformer());
  },
  get functionReader() {
    return (functionReader = functionReader || new FunctionReader(new AwsFunctionTransformer()));
  },
  get evaluateCloudFormationChangeSetExecutionService() {
    return (
      evaluateCloudFormationChangeSetExecutionService ||
      (evaluateCloudFormationChangeSetExecutionService = new EvaluateCloudFormationChangeSetExecutionService(
        AngularServices.executionService,
      ))
    );
  },
};

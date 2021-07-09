import IInjectorService = angular.auto.IInjectorService;

import { FunctionReader, ReactInject } from '@spinnaker/core';
import { EvaluateCloudFormationChangeSetExecutionService } from '../pipeline/stages/deployCloudFormation/evaluateCloudFormationChangeSetExecution.service';

import { AwsServerGroupConfigurationService } from '../serverGroup/configure/serverGroupConfiguration.service';
import { AwsServerGroupTransformer } from '../serverGroup/serverGroup.transformer';

// prettier-ignore
export class AwsReactInject extends ReactInject {
  public get awsInstanceTypeService() { return this.$injector.get('awsInstanceTypeService') as any; }
  public get awsServerGroupCommandBuilder() { return this.$injector.get('awsServerGroupCommandBuilder') as any; }
  public get awsServerGroupConfigurationService() { return this.$injector.get('awsServerGroupConfigurationService') as AwsServerGroupConfigurationService; }
  public get awsServerGroupTransformer() { return this.$injector.get('awsServerGroupTransformer') as AwsServerGroupTransformer; }

  public get functionReader() { return this.$injector.get('functionReader') as FunctionReader; }
  public get evaluateCloudFormationChangeSetExecutionService() { return this.$injector.get('evaluateCloudFormationChangeSetExecutionService') as EvaluateCloudFormationChangeSetExecutionService; }
  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }
}

export const AwsReactInjector: AwsReactInject = new AwsReactInject();

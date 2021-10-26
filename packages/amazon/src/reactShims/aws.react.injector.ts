import IInjectorService = angular.auto.IInjectorService;

import type { FunctionReader } from '@spinnaker/core';
import { ReactInject } from '@spinnaker/core';
import type { EvaluateCloudFormationChangeSetExecutionService } from '../pipeline/stages/deployCloudFormation/evaluateCloudFormationChangeSetExecution.service';
import type { AwsServerGroupCommandBuilder } from '../serverGroup/configure/serverGroupCommandBuilder.service';

import type { AwsServerGroupConfigurationService } from '../serverGroup/configure/serverGroupConfiguration.service';
import type { AwsServerGroupTransformer } from '../serverGroup/serverGroup.transformer';

// prettier-ignore
export class AwsReactInject extends ReactInject {
  public get awsInstanceTypeService() { return this.$injector.get('awsInstanceTypeService') as any; }
  public get awsServerGroupCommandBuilder() { return this.$injector.get('awsServerGroupCommandBuilder') as AwsServerGroupCommandBuilder; }
  public get awsServerGroupConfigurationService() { return this.$injector.get('awsServerGroupConfigurationService') as AwsServerGroupConfigurationService; }
  public get awsServerGroupTransformer() { return this.$injector.get('awsServerGroupTransformer') as AwsServerGroupTransformer; }

  public get functionReader() { return this.$injector.get('functionReader') as FunctionReader; }
  public get evaluateCloudFormationChangeSetExecutionService() { return this.$injector.get('evaluateCloudFormationChangeSetExecutionService') as EvaluateCloudFormationChangeSetExecutionService; }
  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }
}

export const AwsReactInjector: AwsReactInject = new AwsReactInject();

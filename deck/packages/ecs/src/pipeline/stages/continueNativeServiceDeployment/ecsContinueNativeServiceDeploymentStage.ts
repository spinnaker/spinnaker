import type { IStage } from '@spinnaker/core';
import { Registry } from '@spinnaker/core';

import { EcsNativeServiceDeploymentStageConfig } from '../common/EcsNativeServiceDeploymentStageConfig';
import { ecsNativeServiceDeploymentStageValidators } from '../common/ecsNativeStageValidation';

export function registerEcsNativeContinueServiceDeploymentStage(): void {
  Registry.pipeline.registerStage({
    key: 'ecsNativeContinueServiceDeployment',
    label: 'Continue ECS Native Service Deployment',
    description: 'Continues or rolls back a native ECS blue/green deployment paused at its lifecycle hook',
    cloudProvider: 'ecs',
    component: EcsNativeServiceDeploymentStageConfig,
    accountExtractor: (stage: IStage) => [stage.context.credentials],
    configAccountExtractor: (stage: IStage) => [stage.credentials],
    validators: ecsNativeServiceDeploymentStageValidators(true),
  });
}

import type { IStage } from '@spinnaker/core';
import { Registry } from '@spinnaker/core';

import { EcsNativeServiceDeploymentStageConfig } from '../common/EcsNativeServiceDeploymentStageConfig';
import { ecsNativeServiceDeploymentStageValidators } from '../common/ecsNativeStageValidation';

export function registerEcsNativeStopServiceDeploymentStage(): void {
  Registry.pipeline.registerStage({
    key: 'ecsNativeStopServiceDeployment',
    label: 'Stop ECS Native Service Deployment',
    description: 'Stops a native ECS service deployment and rolls it back',
    cloudProvider: 'ecs',
    component: EcsNativeServiceDeploymentStageConfig,
    accountExtractor: (stage: IStage) => [stage.context.credentials],
    configAccountExtractor: (stage: IStage) => [stage.credentials],
    validators: ecsNativeServiceDeploymentStageValidators(false),
  });
}

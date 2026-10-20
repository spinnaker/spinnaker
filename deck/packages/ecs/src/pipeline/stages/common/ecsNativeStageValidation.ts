import type { ICustomValidator, IPipeline, IStage, IValidatorConfig } from '@spinnaker/core';
import { PipelineConfigService } from '@spinnaker/core';

const ECS_NATIVE = 'ecs-native';

const isBlank = (value: unknown): boolean => typeof value !== 'string' || value.trim() === '';

/** A createServerGroup stage, or a Deck deploy stage with a cluster, that deploys through ecs-native. */
export const isEcsNativeDeployStage = (candidate: IStage): boolean =>
  (candidate.type === 'createServerGroup' && candidate.cloudProvider === ECS_NATIVE) ||
  (candidate.type === 'deploy' &&
    (candidate.clusters || []).some((cluster: any) => cluster.cloudProvider === ECS_NATIVE));

/**
 * Continue/Stop stages act on one exact ECS service deployment. Orca resolves it from the
 * `deploymentStageRefId` stage, otherwise from the nearest native deploy stage anywhere upstream
 * (a Manual Judgment or other stages may sit in between), otherwise from the legacy
 * `ecsNativeExpectedServiceDeploymentArn` field. Without any of those the stage fails at runtime.
 */
export function validateEcsNativeDeploymentReference(pipeline: IPipeline, stage: IStage): string {
  if (!isBlank(stage.deploymentStageRefId) || !isBlank(stage.ecsNativeExpectedServiceDeploymentArn)) {
    return null;
  }
  const hasUpstreamNativeDeploy = PipelineConfigService.getAllUpstreamDependencies(pipeline, stage).some(
    isEcsNativeDeployStage,
  );
  return hasUpstreamNativeDeploy
    ? null
    : 'Select the preceding native ECS <strong>Deployment stage</strong> (or set the legacy expected service deployment ARN).';
}

/**
 * A missing lifecycle action means CONTINUE. An expression that was cleared, however, is stored as
 * an empty string and would silently turn a judgment-driven action into CONTINUE.
 */
export function validateEcsNativeLifecycleAction(_pipeline: IPipeline, stage: IStage): string {
  const { lifecycleAction } = stage;
  if (lifecycleAction !== undefined && lifecycleAction !== null && isBlank(lifecycleAction)) {
    return 'The <strong>Action</strong> expression must not be empty; it has to evaluate to CONTINUE or ROLLBACK.';
  }
  return null;
}

const customValidator = (validate: (pipeline: IPipeline, stage: IStage) => string): ICustomValidator => ({
  type: 'custom',
  validate,
});

export const ecsNativeServiceDeploymentStageValidators = (includeLifecycleAction: boolean): IValidatorConfig[] => [
  { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  { type: 'requiredField', fieldName: 'region' },
  { type: 'requiredField', fieldName: 'serverGroupName' },
  customValidator(validateEcsNativeDeploymentReference),
  ...(includeLifecycleAction ? [customValidator(validateEcsNativeLifecycleAction)] : []),
];

import { PipelineConfigValidator, Registry } from '@spinnaker/core';

import { validateEcsNativeDeploymentReference, validateEcsNativeLifecycleAction } from './ecsNativeStageValidation';
import { registerEcsPipelineStages } from '../../../ecs.module';

const nativeDeploy = { refId: 'deploy', type: 'createServerGroup', cloudProvider: 'ecs-native', name: 'Deploy' } as any;
const classicDeploy = { refId: 'deploy', type: 'createServerGroup', cloudProvider: 'ecs', name: 'Deploy' } as any;
const deckDeploy = {
  refId: 'deploy',
  type: 'deploy',
  name: 'Deploy',
  clusters: [{ cloudProvider: 'ecs-native', application: 'app' }],
} as any;
const judgment = { refId: 'judge', type: 'manualJudgment', name: 'Approve', requisiteStageRefIds: ['deploy'] } as any;
const pipelineWith = (...stages: any[]) => ({ stages, triggers: [] } as any);

describe('validateEcsNativeDeploymentReference', () => {
  it('passes with an explicit deploymentStageRefId', () => {
    const stage = { refId: 'continue', requisiteStageRefIds: ['judge'], deploymentStageRefId: 'deploy' } as any;
    expect(validateEcsNativeDeploymentReference(pipelineWith(nativeDeploy, judgment, stage), stage)).toBeNull();
  });

  it('passes with the legacy expected service deployment ARN', () => {
    const stage = { refId: 'continue', ecsNativeExpectedServiceDeploymentArn: 'arn:aws:ecs:x' } as any;
    expect(validateEcsNativeDeploymentReference(pipelineWith(stage), stage)).toBeNull();
  });

  it('passes when a direct requisite is a native deploy stage', () => {
    const stage = { refId: 'continue', requisiteStageRefIds: ['deploy'] } as any;
    expect(validateEcsNativeDeploymentReference(pipelineWith(nativeDeploy, stage), stage)).toBeNull();
  });

  it('passes when the native deploy sits upstream behind a Manual Judgment, as Orca resolves it', () => {
    const stage = { refId: 'continue', requisiteStageRefIds: ['judge'] } as any;
    expect(validateEcsNativeDeploymentReference(pipelineWith(nativeDeploy, judgment, stage), stage)).toBeNull();
    expect(validateEcsNativeDeploymentReference(pipelineWith(deckDeploy, judgment, stage), stage)).toBeNull();
  });

  it('fails when no native deploy is anywhere upstream', () => {
    const stage = { refId: 'continue', requisiteStageRefIds: ['judge'], deploymentStageRefId: ' ' } as any;
    expect(validateEcsNativeDeploymentReference(pipelineWith(classicDeploy, judgment, stage), stage)).toContain(
      'Deployment stage',
    );
    expect(validateEcsNativeDeploymentReference(pipelineWith(stage), stage)).toContain('Deployment stage');
  });
});

describe('validateEcsNativeLifecycleAction', () => {
  it('accepts a missing action (CONTINUE), fixed actions and expressions', () => {
    [undefined, 'CONTINUE', 'ROLLBACK', "${ #judgment('Approve') == 'continue' ? 'CONTINUE' : 'ROLLBACK' }"].forEach(
      (lifecycleAction) => {
        expect(validateEcsNativeLifecycleAction(null, { lifecycleAction } as any)).toBeNull();
      },
    );
  });

  it('rejects a cleared expression', () => {
    expect(validateEcsNativeLifecycleAction(null, { lifecycleAction: '  ' } as any)).toContain('Action');
  });
});

describe('ECS native Continue/Stop stage validation', () => {
  beforeEach(() => {
    Registry.reinitialize();
    registerEcsPipelineStages();
  });

  const messagesFor = async (stage: any, ...others: any[]) => {
    const results = await PipelineConfigValidator.validatePipeline(pipelineWith(...others, stage));
    return results.stages
      .filter((result) => result.stage === stage)
      .reduce((messages, result) => messages.concat(result.messages), [] as string[]);
  };

  const baseStage = (type: string, overrides: any = {}) => ({
    credentials: 'ecs-prod',
    name: type,
    refId: 'lifecycle',
    region: 'us-west-2',
    requisiteStageRefIds: ['judge'],
    serverGroupName: 'api-main',
    type,
    ...overrides,
  });

  it('registers both stages', () => {
    expect(Registry.pipeline.getStageConfig({ type: 'ecsNativeContinueServiceDeployment' } as any)).toBeTruthy();
    expect(Registry.pipeline.getStageConfig({ type: 'ecsNativeStopServiceDeployment' } as any)).toBeTruthy();
  });

  it('flags Continue and Stop stages with no native deploy upstream and no explicit reference', async () => {
    for (const type of ['ecsNativeContinueServiceDeployment', 'ecsNativeStopServiceDeployment']) {
      const messages = await messagesFor(baseStage(type), classicDeploy, judgment);
      expect(messages.some((message) => message.includes('Deployment stage'))).toBe(true);
    }
  });

  it('accepts Continue and Stop stages that follow a native deploy through a Manual Judgment', async () => {
    for (const type of ['ecsNativeContinueServiceDeployment', 'ecsNativeStopServiceDeployment']) {
      const messages = await messagesFor(baseStage(type), nativeDeploy, judgment);
      expect(messages.some((message) => message.includes('Deployment stage'))).toBe(false);
    }
  });

  it('flags an empty Continue expression but not the Stop stage, which has no action', async () => {
    const continueMessages = await messagesFor(
      baseStage('ecsNativeContinueServiceDeployment', { deploymentStageRefId: 'deploy', lifecycleAction: '' }),
      nativeDeploy,
      judgment,
    );
    expect(continueMessages.some((message) => message.includes('Action'))).toBe(true);

    const stopMessages = await messagesFor(
      baseStage('ecsNativeStopServiceDeployment', { deploymentStageRefId: 'deploy', lifecycleAction: '' }),
      nativeDeploy,
      judgment,
    );
    expect(stopMessages).toEqual([]);
  });

  it('passes a fully configured Continue stage', async () => {
    const messages = await messagesFor(
      baseStage('ecsNativeContinueServiceDeployment', { deploymentStageRefId: 'deploy', lifecycleAction: 'CONTINUE' }),
      nativeDeploy,
      judgment,
    );
    expect(messages).toEqual([]);
  });
});

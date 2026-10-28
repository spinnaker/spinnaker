import { fireEvent, render, waitFor } from '@testing-library/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import { EcsNativeServiceDeploymentStageConfig } from './EcsNativeServiceDeploymentStageConfig';

describe('EcsNativeServiceDeploymentStageConfig', () => {
  const stage = {
    credentials: 'ecs-account',
    region: 'us-west-2',
    serverGroupName: 'orders',
    deploymentStageRefId: 'deploy-ref',
    requisiteStageRefIds: ['deploy-ref'],
    ecsNativeExpectedServiceDeploymentArn: 'arn:aws:ecs:deployment/orders/7',
  };
  const pipeline = {
    stages: [
      { cloudProvider: 'ecs-native', name: 'Deploy orders', refId: 'deploy-ref', type: 'createServerGroup' },
      { ...stage, name: 'Continue orders', refId: 'continue-ref', type: 'ecsNativeContinueServiceDeployment' },
    ],
  };

  const select = (container: HTMLElement, name: string) =>
    container.querySelector(`select[name="${name}"]`) as HTMLSelectElement;
  const input = (container: HTMLElement, name: string) =>
    container.querySelector(`input[name="${name}"]`) as HTMLInputElement;

  beforeEach(() => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([{ name: 'ecs-account', type: 'ecs' }] as any);
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockResolvedValue([]);
  });

  it('references an upstream native deploy and keeps the ARN as an optional legacy fallback', async () => {
    const updateStageField = vi.fn();
    const { container } = render(
      <EcsNativeServiceDeploymentStageConfig
        application={{ defaultCredentials: {}, defaultRegions: {} } as any}
        pipeline={pipeline as any}
        stage={stage as any}
        updateStageField={updateStageField}
        stageFieldUpdated={() => undefined}
      />,
    );

    await waitFor(() => expect(select(container, 'credentials')).toHaveValue('ecs-account'));
    expect(select(container, 'deploymentStageRefId')).toHaveValue('deploy-ref');
    expect(input(container, 'ecsNativeExpectedServiceDeploymentArn')).not.toBeRequired();
    expect(input(container, 'ecsNativeExpectedServiceDeploymentArn')).toHaveValue('arn:aws:ecs:deployment/orders/7');

    fireEvent.change(select(container, 'deploymentStageRefId'), { target: { value: 'deploy-ref' } });
    expect(updateStageField).toHaveBeenCalledWith({ deploymentStageRefId: 'deploy-ref' });
  });

  describe('with a Manual Judgment between deploy and this stage', () => {
    const judgedPipeline = {
      stages: [
        pipeline.stages[0],
        { name: 'Approve release', refId: 'judge-ref', type: 'manualJudgment', requisiteStageRefIds: ['deploy-ref'] },
      ],
    };
    const renderJudged = (overrides: Record<string, any>) => {
      const updateStageField = vi.fn();
      const judgedStage = { ...stage, requisiteStageRefIds: ['judge-ref'], ...overrides };
      const rendered = render(
        <EcsNativeServiceDeploymentStageConfig
          application={{ defaultCredentials: {}, defaultRegions: {} } as any}
          pipeline={{ stages: [...judgedPipeline.stages, { ...judgedStage, refId: 'continue-ref' }] } as any}
          stage={judgedStage as any}
          updateStageField={updateStageField}
          stageFieldUpdated={() => undefined}
        />,
      );
      return { container: rendered.container, updateStageField };
    };

    it('selecting the deploy stage never rewrites requisiteStageRefIds, so the judgment gate stays', () => {
      const { container, updateStageField } = renderJudged({ deploymentStageRefId: undefined });
      fireEvent.change(select(container, 'deploymentStageRefId'), { target: { value: 'deploy-ref' } });
      expect(updateStageField).toHaveBeenCalledWith({ deploymentStageRefId: 'deploy-ref' });
      updateStageField.mock.calls.forEach(([changes]: [Record<string, any>]) => {
        expect(changes.requisiteStageRefIds).toBeUndefined();
      });
    });

    it('offers the upstream deploy stage through the judgment', () => {
      const { container } = renderJudged({ deploymentStageRefId: 'deploy-ref' });
      const options = Array.from(select(container, 'deploymentStageRefId').options).map((option) => option.value);
      expect(options).toEqual(['', 'deploy-ref']);
      expect(select(container, 'deploymentStageRefId')).toHaveValue('deploy-ref');
    });

    it('does not present the judgment refId as the selected deploy stage', () => {
      const { container } = renderJudged({ deploymentStageRefId: undefined });
      expect(select(container, 'deploymentStageRefId')).toHaveValue('');
    });
  });

  describe('lifecycle action', () => {
    const renderConfig = (lifecycleAction: string | undefined, updateStageField = vi.fn()) => {
      const continuePipeline = {
        stages: [
          ...pipeline.stages,
          { name: 'Approve release', refId: 'judge-ref', type: 'manualJudgment', requisiteStageRefIds: ['deploy-ref'] },
        ],
      };
      const continueStage = {
        ...stage,
        type: 'ecsNativeContinueServiceDeployment',
        requisiteStageRefIds: ['judge-ref'],
        lifecycleAction,
      };
      const rendered = render(
        <EcsNativeServiceDeploymentStageConfig
          application={{ defaultCredentials: {}, defaultRegions: {} } as any}
          pipeline={continuePipeline as any}
          stage={continueStage as any}
          updateStageField={updateStageField}
          stageFieldUpdated={() => undefined}
        />,
      );
      return { container: rendered.container, updateStageField };
    };
    const expression = "${ #judgment('Approve release') == 'continue' ? 'CONTINUE' : 'ROLLBACK' }";

    it('shows an existing expression in expression mode with its text', () => {
      const { container } = renderConfig(expression);
      expect(select(container, 'lifecycleActionMode')).toHaveValue('EXPRESSION');
      expect(input(container, 'lifecycleAction')).toHaveValue(expression);
    });

    it('shows fixed actions without an expression field', () => {
      const { container } = renderConfig('ROLLBACK');
      expect(select(container, 'lifecycleActionMode')).toHaveValue('ROLLBACK');
      expect(input(container, 'lifecycleAction')).toBeNull();
    });

    it('pre-fills a judgment expression when switching to expression mode', () => {
      const { container, updateStageField } = renderConfig('CONTINUE');
      fireEvent.change(select(container, 'lifecycleActionMode'), { target: { value: 'EXPRESSION' } });
      expect(updateStageField).toHaveBeenCalledWith({ lifecycleAction: expression });
    });

    it('stores the fixed action when switching away from an expression', () => {
      const { container, updateStageField } = renderConfig(expression);
      fireEvent.change(select(container, 'lifecycleActionMode'), { target: { value: 'ROLLBACK' } });
      expect(updateStageField).toHaveBeenCalledWith({ lifecycleAction: 'ROLLBACK' });
    });
  });
});

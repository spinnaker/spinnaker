import React from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import { AccountSelectInput, AccountService, PipelineConfigService, StageConfigField } from '@spinnaker/core';

import { isEcsNativeDeployStage } from './ecsNativeStageValidation';

const ECS_NATIVE = 'ecs-native';

export function EcsNativeServiceDeploymentStageConfig({
  application,
  pipeline,
  stage,
  updateStageField,
}: IStageConfigProps) {
  const [accounts, setAccounts] = React.useState<IAccount[]>([]);
  const upstreamDeployStages = PipelineConfigService.getAllUpstreamDependencies(pipeline, stage).filter(
    isEcsNativeDeployStage,
  );
  const isStop = stage.type === 'ecsNativeStopServiceDeployment';
  const isFixedAction = (value?: string) => value === 'CONTINUE' || value === 'ROLLBACK';
  const [expressionMode, setExpressionMode] = React.useState<boolean>(
    !!stage.lifecycleAction && !isFixedAction(stage.lifecycleAction),
  );
  const actionMode = expressionMode ? 'EXPRESSION' : stage.lifecycleAction || 'CONTINUE';
  const upstreamJudgment = PipelineConfigService.getAllUpstreamDependencies(pipeline, stage).find(
    (candidate) => candidate.type === 'manualJudgment',
  );
  const selectLifecycleAction = (mode: string) => {
    if (mode !== 'EXPRESSION') {
      setExpressionMode(false);
      updateStageField({ lifecycleAction: mode });
      return;
    }
    setExpressionMode(true);
    if (!stage.lifecycleAction || isFixedAction(stage.lifecycleAction)) {
      // Pre-fill an expression for the common case: a Manual Judgment with "continue"/"rollback" options.
      const judgmentName = upstreamJudgment?.name || 'Approve release';
      updateStageField({
        lifecycleAction: `\${ #judgment('${judgmentName}') == 'continue' ? 'CONTINUE' : 'ROLLBACK' }`,
      });
    }
  };
  // Only show a direct requisite as the selection when it actually is a native deploy stage. A
  // Manual Judgment between deploy and Continue is the documented pattern, and its refId is not an
  // option in this select.
  const directDeployRequisite = (stage.requisiteStageRefIds || []).find((refId: string | number) =>
    upstreamDeployStages.some((deployStage) => String(deployStage.refId) === String(refId)),
  );
  const deploymentStageRefId = String(stage.deploymentStageRefId || directDeployRequisite || '');

  React.useEffect(() => {
    let active = true;
    AccountService.listAccounts('ecs').then((loadedAccounts) => active && setAccounts(loadedAccounts));
    return () => {
      active = false;
    };
  }, []);

  React.useEffect(() => {
    const changes: Record<string, any> = { cloudProvider: ECS_NATIVE };
    const defaultCredentials = application.defaultCredentials?.ecs;
    const defaultRegion = application.defaultRegions?.ecs;
    if (!stage.credentials && defaultCredentials) {
      changes.credentials = defaultCredentials;
    }
    if (!stage.region && defaultRegion) {
      changes.region = defaultRegion;
    }
    updateStageField(changes);
  }, []);

  // Store only the reference. Every option is already upstream of this stage, so the pipeline graph
  // must stay untouched: rewriting requisiteStageRefIds would drop a Manual Judgment between the
  // deploy and this stage and let a fixed CONTINUE run without the human gate.
  const selectDeploymentStage = (refId: string) => {
    updateStageField({ deploymentStageRefId: refId || undefined });
  };

  return (
    <div className="container-fluid form-horizontal">
      <StageConfigField label="Account">
        <AccountSelectInput
          accounts={accounts}
          name="credentials"
          onChange={(event: React.ChangeEvent<HTMLSelectElement>) =>
            updateStageField({ credentials: event.target.value })
          }
          provider="ecs"
          value={stage.credentials || ''}
        />
      </StageConfigField>
      <StageConfigField label="Region">
        <input
          className="form-control input-sm"
          name="region"
          onChange={(event) => updateStageField({ region: event.target.value })}
          required
          type="text"
          value={stage.region || ''}
        />
      </StageConfigField>
      <StageConfigField label="Server group name">
        <input
          className="form-control input-sm"
          name="serverGroupName"
          onChange={(event) => updateStageField({ serverGroupName: event.target.value })}
          required
          type="text"
          value={stage.serverGroupName || ''}
        />
      </StageConfigField>
      <StageConfigField label="Deployment stage">
        <select
          className="form-control input-sm"
          name="deploymentStageRefId"
          onChange={(event) => selectDeploymentStage(event.target.value)}
          value={deploymentStageRefId}
        >
          <option value="">Select a preceding native deploy stage</option>
          {upstreamDeployStages.map((deployStage) => (
            <option key={deployStage.refId} value={deployStage.refId}>
              {deployStage.name || deployStage.refId}
            </option>
          ))}
        </select>
        <span className="help-block">The deploy stage supplies the exact ECS service-deployment ARN.</span>
      </StageConfigField>
      {isStop ? (
        <StageConfigField label="Stop behavior">
          <span className="help-block">
            Stops the deployment and rolls the service back to its previous revision (ECS stop type ROLLBACK).
          </span>
        </StageConfigField>
      ) : (
        <StageConfigField label="Action">
          <select
            className="form-control input-sm"
            name="lifecycleActionMode"
            onChange={(event) => selectLifecycleAction(event.target.value)}
            value={actionMode}
          >
            <option value="CONTINUE">Continue (approve)</option>
            <option value="ROLLBACK">Roll back (reject)</option>
            <option value="EXPRESSION">Expression (e.g. from a Manual Judgment)</option>
          </select>
          {actionMode === 'EXPRESSION' && (
            <input
              className="form-control input-sm"
              name="lifecycleAction"
              onChange={(event) => updateStageField({ lifecycleAction: event.target.value })}
              placeholder="${ #judgment('Approve release') == 'continue' ? 'CONTINUE' : 'ROLLBACK' }"
              type="text"
              value={stage.lifecycleAction || ''}
            />
          )}
          <span className="help-block">
            Acts on the PAUSE hook configured on the deploy stage. The expression must evaluate to CONTINUE or ROLLBACK.
          </span>
        </StageConfigField>
      )}
      <StageConfigField label="Expected service deployment ARN (legacy fallback)">
        <input
          className="form-control input-sm"
          name="ecsNativeExpectedServiceDeploymentArn"
          onChange={(event) => updateStageField({ ecsNativeExpectedServiceDeploymentArn: event.target.value })}
          type="text"
          value={stage.ecsNativeExpectedServiceDeploymentArn || ''}
        />
      </StageConfigField>
    </div>
  );
}

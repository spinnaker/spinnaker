import React from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import {
  AccountRegionClusterSelector,
  AccountService,
  ChecklistInput,
  StageConfigField,
  StageConstants,
} from '@spinnaker/core';

const ACTIONS = [
  { label: 'Suspend', value: 'suspend' },
  { label: 'Resume', value: 'resume' },
];

const SCALING_PROCESSES = [
  'Launch',
  'Terminate',
  'AddToLoadBalancer',
  'AlarmNotification',
  'AZRebalance',
  'HealthCheck',
  'ReplaceUnhealthy',
  'ScheduledActions',
];

export function ModifyScalingProcessStageConfig({
  application,
  pipeline,
  stage,
  updateStage,
  updateStageField,
}: IStageConfigProps) {
  const [accounts, setAccounts] = React.useState<IAccount[]>([]);

  React.useEffect(() => {
    let mounted = true;
    AccountService.listAccounts('aws').then((loadedAccounts) => mounted && setAccounts(loadedAccounts));
    return () => {
      mounted = false;
    };
  }, []);

  React.useEffect(() => {
    const defaults: Record<string, any> = {};
    const defaultCredentials = application.defaultCredentials.aws;
    const defaultRegion = application.defaultRegions.aws;

    if (!stage.cloudProvider) {
      defaults.cloudProvider = 'aws';
    }
    if (!stage.processes) {
      defaults.processes = [];
    }
    if (!stage.regions) {
      defaults.regions = defaultRegion ? [defaultRegion] : [];
    } else if (!stage.regions.length && defaultRegion) {
      defaults.regions = [defaultRegion];
    }
    if (!stage.credentials && defaultCredentials) {
      defaults.credentials = defaultCredentials;
    }
    if (!stage.action) {
      defaults.action = ACTIONS[0].value;
    }
    if (!stage.target) {
      defaults.target = StageConstants.TARGET_LIST[0].val;
    }
    if (stage.suspendProcesses !== undefined || stage.resumeProcesses !== undefined) {
      defaults.suspendProcesses = undefined;
      defaults.resumeProcesses = undefined;
    }
    if (Object.keys(defaults).length) {
      updateStageField(defaults);
    }
  }, [
    application.defaultCredentials.aws,
    application.defaultRegions.aws,
    stage.action,
    stage.cloudProvider,
    stage.credentials,
    stage.processes,
    stage.regions,
    stage.resumeProcesses,
    stage.suspendProcesses,
    stage.target,
    updateStageField,
  ]);

  return (
    <div className="form-horizontal">
      {!pipeline.strategy && (
        <AccountRegionClusterSelector
          accounts={accounts}
          application={application}
          component={{ ...stage }}
          onComponentUpdate={updateStage}
        />
      )}
      <StageConfigField label="Target">
        <select
          className="form-control input-sm"
          name="target"
          onChange={(event) => updateStageField({ target: event.target.value })}
          value={stage.target || StageConstants.TARGET_LIST[0].val}
        >
          {StageConstants.TARGET_LIST.map((target) => (
            <option key={target.val} title={target.description} value={target.val}>
              {target.label}
            </option>
          ))}
        </select>
      </StageConfigField>
      <StageConfigField label="Action">
        <select
          className="form-control input-sm"
          name="action"
          onChange={(event) =>
            updateStageField({
              action: event.target.value,
              suspendProcesses: undefined,
              resumeProcesses: undefined,
            })
          }
          value={stage.action || ACTIONS[0].value}
        >
          {ACTIONS.map((action) => (
            <option key={action.value} value={action.value}>
              {action.label}
            </option>
          ))}
        </select>
      </StageConfigField>
      <StageConfigField label="Processes">
        <ChecklistInput
          name="processes"
          onChange={(event: any) => updateStageField({ processes: event.target.value })}
          showSelectAll={true}
          stringOptions={SCALING_PROCESSES}
          value={stage.processes || []}
        />
      </StageConfigField>
    </div>
  );
}

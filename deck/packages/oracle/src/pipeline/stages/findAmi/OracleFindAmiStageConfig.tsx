import React, { useEffect, useState } from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import { AccountRegionClusterSelector, AccountService, Registry, StageConfigField } from '@spinnaker/core';

const selectionStrategies = [
  {
    label: 'Largest',
    val: 'LARGEST',
    description: 'When multiple server groups exist, prefer the server group with the most instances',
  },
  { label: 'Newest', val: 'NEWEST', description: 'When multiple server groups exist, prefer the newest' },
  { label: 'Oldest', val: 'OLDEST', description: 'When multiple server groups exist, prefer the oldest' },
  { label: 'Fail', val: 'FAIL', description: 'When multiple server groups exist, fail' },
];

export function OracleFindAmiStageConfig(props: IStageConfigProps) {
  const { application, stage, stageFieldUpdated, updateStageField } = props;
  const [accounts, setAccounts] = useState<IAccount[]>([]);

  useEffect(() => {
    const changes: Record<string, any> = {};
    if (stage.cloudProvider !== 'oracle') {
      changes.cloudProvider = 'oracle';
    }
    if (!stage.credentials && application.defaultCredentials?.oracle) {
      changes.credentials = application.defaultCredentials.oracle;
    }
    if (!stage.regions?.length && application.defaultRegions?.oracle) {
      changes.regions = [application.defaultRegions.oracle];
    }
    if (!stage.selectionStrategy) {
      changes.selectionStrategy = selectionStrategies[0].val;
    }
    if (stage.onlyEnabled === undefined) {
      changes.onlyEnabled = true;
    }
    if (Object.keys(changes).length) {
      updateStageField(changes);
    }
  }, []);

  useEffect(() => {
    let active = true;
    AccountService.listAccounts('oracle').then((loadedAccounts) => active && setAccounts(loadedAccounts));
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="form-horizontal">
      <AccountRegionClusterSelector
        accounts={accounts}
        application={application}
        component={stage}
        onComponentUpdate={stageFieldUpdated}
      />
      <StageConfigField label="Server Group Selection">
        <select
          className="form-control input-sm"
          onChange={(event) => updateStageField({ selectionStrategy: event.target.value })}
          value={stage.selectionStrategy || selectionStrategies[0].val}
        >
          {selectionStrategies.map((strategy) => (
            <option key={strategy.val} title={strategy.description} value={strategy.val}>
              {strategy.label}
            </option>
          ))}
        </select>
      </StageConfigField>
      <StageConfigField label="Server Group Filters">
        <label className="checkbox-inline">
          <input
            checked={stage.onlyEnabled !== false}
            onChange={(event) => updateStageField({ onlyEnabled: event.target.checked })}
            type="checkbox"
          />
          Only consider enabled Server Groups
        </label>
      </StageConfigField>
    </div>
  );
}

export const oracleFindAmiStage = {
  key: 'findImage',
  provides: 'findImage',
  cloudProvider: 'oracle',
  component: OracleFindAmiStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials' },
  ],
};

export function registerOracleFindAmiStage() {
  Registry.pipeline.registerStage(oracleFindAmiStage);
}

registerOracleFindAmiStage();

import React from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import { AccountRegionClusterSelector, AccountService, StageConfigField, StageConstants } from '@spinnaker/core';

const ACTIONS = [
  { label: 'Upsert', value: 'upsert' },
  { label: 'Delete', value: 'delete' },
];

const POOL_STATES = ['Stopped', 'Running', 'Hibernated'];

export function ModifyWarmPoolStageConfig({
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
    if (!stage.poolState) {
      defaults.poolState = POOL_STATES[0];
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
    stage.poolState,
    stage.regions,
    stage.target,
    updateStageField,
  ]);

  const isUpsert = (stage.action || ACTIONS[0].value) === 'upsert';

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
          onChange={(event) => updateStageField({ action: event.target.value })}
          value={stage.action || ACTIONS[0].value}
        >
          {ACTIONS.map((action) => (
            <option key={action.value} value={action.value}>
              {action.label}
            </option>
          ))}
        </select>
      </StageConfigField>
      {isUpsert && (
        <>
          <StageConfigField label="Min Size">
            <input
              className="form-control input-sm"
              type="number"
              min={0}
              name="minSize"
              onChange={(event) => updateStageField({ minSize: Number(event.target.value) })}
              value={stage.minSize ?? 0}
            />
          </StageConfigField>
          <StageConfigField label="Max Group Prepared Capacity">
            <input
              className="form-control input-sm"
              type="number"
              min={-1}
              name="maxGroupPreparedCapacity"
              onChange={(event) => updateStageField({ maxGroupPreparedCapacity: Number(event.target.value) })}
              value={stage.maxGroupPreparedCapacity ?? -1}
            />
          </StageConfigField>
          <StageConfigField label="Instance State">
            <select
              className="form-control input-sm"
              name="poolState"
              onChange={(event) => updateStageField({ poolState: event.target.value })}
              value={stage.poolState || POOL_STATES[0]}
            >
              {POOL_STATES.map((state) => (
                <option key={state} value={state}>
                  {state}
                </option>
              ))}
            </select>
          </StageConfigField>
          <StageConfigField label="Reuse Instances on Scale In">
            <input
              type="checkbox"
              checked={!!stage.reuseOnScaleIn}
              onChange={(event) => updateStageField({ reuseOnScaleIn: event.target.checked })}
            />
          </StageConfigField>
        </>
      )}
    </div>
  );
}

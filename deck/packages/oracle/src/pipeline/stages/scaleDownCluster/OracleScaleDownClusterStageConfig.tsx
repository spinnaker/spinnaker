import React, { useEffect, useState } from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import { AccountRegionClusterSelector, AccountService, Registry, StageConfigField } from '@spinnaker/core';

function pluralize(str: string, val: number): string {
  return val === 1 ? str : `${str}s`;
}

export function OracleScaleDownClusterStageConfig(props: IStageConfigProps) {
  const { application, pipeline, stage, stageFieldUpdated, updateStageField } = props;
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
    if (stage.remainingFullSizeServerGroups === undefined) {
      changes.remainingFullSizeServerGroups = 1;
    }
    if (stage.allowScaleDownActive === undefined) {
      changes.allowScaleDownActive = false;
    }
    if (stage.preferLargerOverNewer === undefined) {
      changes.preferLargerOverNewer = 'false';
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
      {!pipeline.strategy && (
        <AccountRegionClusterSelector
          accounts={accounts}
          application={application}
          component={stage}
          onComponentUpdate={stageFieldUpdated}
        />
      )}
      <StageConfigField label="Scale Down Options">
        <div className="form-inline">
          <p>
            Keep the{' '}
            <input
              className="form-control input-sm"
              min={0}
              onChange={(event) => updateStageField({ remainingFullSizeServerGroups: Number(event.target.value) })}
              required
              style={{ width: 50 }}
              type="number"
              value={stage.remainingFullSizeServerGroups ?? ''}
            />{' '}
            <select
              className="form-control input-sm"
              onChange={(event) => updateStageField({ preferLargerOverNewer: event.target.value })}
              style={{ width: 100 }}
              value={stage.preferLargerOverNewer ?? 'false'}
            >
              <option value="true">largest</option>
              <option value="false">newest</option>
            </select>{' '}
            {pluralize('server group', stage.remainingFullSizeServerGroups)} at current size.
          </p>
          <p>The remaining server groups will be scaled down to zero instances.</p>
        </div>
      </StageConfigField>
      <div className="form-group">
        <div className="col-md-offset-3 col-md-6 checkbox">
          <label>
            <input
              checked={!!stage.allowScaleDownActive}
              onChange={(event) => updateStageField({ allowScaleDownActive: event.target.checked })}
              type="checkbox"
            />
            Allow scale down of active server groups
          </label>
        </div>
      </div>
    </div>
  );
}

export const oracleScaleDownClusterStage = {
  key: 'scaleDownCluster',
  provides: 'scaleDownCluster',
  cloudProvider: 'oracle',
  component: OracleScaleDownClusterStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    {
      type: 'requiredField',
      fieldName: 'remainingFullSizeServerGroups',
      fieldLabel: 'Keep [X] full size Server Groups',
    },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
  strategy: true,
};

export function registerOracleScaleDownClusterStage() {
  Registry.pipeline.registerStage(oracleScaleDownClusterStage);
}

registerOracleScaleDownClusterStage();

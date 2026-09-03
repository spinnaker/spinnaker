import React, { useEffect, useState } from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import {
  AccountRegionClusterSelector,
  AccountService,
  PlatformHealthOverride,
  Registry,
  StageConfigField,
} from '@spinnaker/core';

function pluralize(str: string, val: number): string {
  return val === 1 ? str : `${str}s`;
}

export function OracleShrinkClusterStageConfig(props: IStageConfigProps) {
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
    if (stage.shrinkToSize === undefined) {
      changes.shrinkToSize = 1;
    }
    if (stage.allowDeleteActive === undefined) {
      changes.allowDeleteActive = false;
    }
    if (stage.retainLargerOverNewer === undefined) {
      changes.retainLargerOverNewer = 'false';
    }
    if (
      stage.isNew &&
      application.attributes.platformHealthOnlyShowOverride &&
      application.attributes.platformHealthOnly &&
      stage.interestingHealthProviderNames === undefined
    ) {
      changes.interestingHealthProviderNames = ['Oracle'];
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
      <StageConfigField label="Shrink Options">
        <div className="form-inline">
          Shrink to{' '}
          <input
            className="form-control input-sm"
            min={0}
            onChange={(event) => updateStageField({ shrinkToSize: Number(event.target.value) })}
            required
            style={{ width: 50 }}
            type="number"
            value={stage.shrinkToSize ?? ''}
          />{' '}
          {pluralize('server group', stage.shrinkToSize)}, keeping the{' '}
          <select
            className="form-control input-sm"
            onChange={(event) => updateStageField({ retainLargerOverNewer: event.target.value })}
            value={stage.retainLargerOverNewer ?? 'false'}
          >
            <option value="true">largest</option>
            <option value="false">newest</option>
          </select>
        </div>
      </StageConfigField>
      <div className="form-group">
        <div className="col-md-offset-3 col-md-6 checkbox">
          <label>
            <input
              checked={!!stage.allowDeleteActive}
              onChange={(event) => updateStageField({ allowDeleteActive: event.target.checked })}
              type="checkbox"
            />
            Allow deletion of active server groups
          </label>
        </div>
      </div>
      {application.attributes.platformHealthOnlyShowOverride && (
        <StageConfigField label="Task Completion">
          <PlatformHealthOverride
            interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
            onChange={(interestingHealthProviderNames) => updateStageField({ interestingHealthProviderNames })}
            platformHealthType="Oracle"
          />
        </StageConfigField>
      )}
    </div>
  );
}

export const oracleShrinkClusterStage = {
  key: 'shrinkCluster',
  provides: 'shrinkCluster',
  cloudProvider: 'oracle',
  component: OracleShrinkClusterStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerOracleShrinkClusterStage() {
  Registry.pipeline.registerStage(oracleShrinkClusterStage);
}

registerOracleShrinkClusterStage();

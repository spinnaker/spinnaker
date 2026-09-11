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

export function AwsShrinkClusterStageConfig(props: IStageConfigProps) {
  const { application, pipeline, stage, stageFieldUpdated, updateStageField } = props;
  const [accounts, setAccounts] = useState<IAccount[]>([]);

  useEffect(() => {
    const changes: Record<string, any> = {};
    if (stage.cloudProvider !== 'aws') {
      changes.cloudProvider = 'aws';
    }
    if (!stage.credentials && application.defaultCredentials?.aws) {
      changes.credentials = application.defaultCredentials.aws;
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
      changes.interestingHealthProviderNames = ['Amazon'];
    }
    if (Object.keys(changes).length) {
      updateStageField(changes);
    }
  }, []);

  useEffect(() => {
    let active = true;
    AccountService.listAccounts('aws').then((loadedAccounts) => active && setAccounts(loadedAccounts));
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
            style={{ width: 100 }}
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
            platformHealthType="Amazon"
          />
        </StageConfigField>
      )}
    </div>
  );
}

export const awsShrinkClusterStage = {
  key: 'shrinkCluster',
  provides: 'shrinkCluster',
  cloudProvider: 'aws',
  component: AwsShrinkClusterStageConfig,
  accountExtractor: (stage: any) => [stage.context.credentials],
  configAccountExtractor: (stage: any) => [stage.credentials],
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerAwsShrinkClusterStage() {
  Registry.pipeline.registerStage(awsShrinkClusterStage);
}

registerAwsShrinkClusterStage();

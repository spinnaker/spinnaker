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

export function AwsDisableClusterStageConfig(props: IStageConfigProps) {
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
    if (stage.remainingEnabledServerGroups === undefined) {
      changes.remainingEnabledServerGroups = 1;
    }
    if (stage.preferLargerOverNewer === undefined) {
      changes.preferLargerOverNewer = 'false';
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
      <StageConfigField label="Disable Options">
        <div className="form-inline">
          Keep the{' '}
          <input
            className="form-control input-sm"
            min={0}
            onChange={(event) => updateStageField({ remainingEnabledServerGroups: Number(event.target.value) })}
            required
            style={{ width: 50 }}
            type="number"
            value={stage.remainingEnabledServerGroups ?? ''}
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
          {pluralize('server group', stage.remainingEnabledServerGroups)} enabled.
        </div>
      </StageConfigField>
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

export const awsDisableClusterStage = {
  key: 'disableCluster',
  provides: 'disableCluster',
  cloudProvider: 'aws',
  component: AwsDisableClusterStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'remainingEnabledServerGroups', fieldLabel: 'Keep [X] enabled Server Groups' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerAwsDisableClusterStage() {
  Registry.pipeline.registerStage(awsDisableClusterStage);
}

registerAwsDisableClusterStage();

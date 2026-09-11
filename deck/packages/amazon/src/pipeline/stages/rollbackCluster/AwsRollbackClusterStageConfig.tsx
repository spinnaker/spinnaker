import React, { useEffect, useState } from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import {
  AccountRegionClusterSelector,
  AccountService,
  PlatformHealthOverride,
  Registry,
  SETTINGS,
  StageConfigField,
} from '@spinnaker/core';

export function AwsRollbackClusterStageConfig(props: IStageConfigProps) {
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
    if (!stage.targetHealthyRollbackPercentage) {
      changes.targetHealthyRollbackPercentage = 100;
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
      {application.attributes.platformHealthOnlyShowOverride && (
        <StageConfigField label="Task Completion">
          <PlatformHealthOverride
            interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
            onChange={(interestingHealthProviderNames) => updateStageField({ interestingHealthProviderNames })}
            platformHealthType="Amazon"
          />
        </StageConfigField>
      )}
      <div className="row">
        {(stage.regions || []).length > 1 && (
          <div className="col-sm-10 col-sm-offset-2">
            Wait{' '}
            <input
              className="form-control input-sm inline-number"
              min={0}
              onChange={(event) => updateStageField({ waitTimeBetweenRegions: Number(event.target.value) })}
              type="number"
              value={stage.waitTimeBetweenRegions ?? ''}
            />{' '}
            seconds between regional rollbacks.
          </div>
        )}
        <div className="col-sm-10 col-sm-offset-2">
          Consider rollback successful when{' '}
          <input
            className="form-control input-sm inline-number"
            max={100}
            min={0}
            onChange={(event) => updateStageField({ targetHealthyRollbackPercentage: Number(event.target.value) })}
            type="number"
            value={stage.targetHealthyRollbackPercentage ?? ''}
          />{' '}
          percent of instances are healthy.
        </div>
        {SETTINGS.feature.dynamicRollbackTimeout && (
          <div className="col-sm-10 col-sm-offset-2">
            Rollback Timeout is{' '}
            <input
              className="form-control input-sm inline-number"
              max={100}
              min={0}
              onChange={(event) => updateStageField({ rollbackTimeout: Number(event.target.value) })}
              type="number"
              value={stage.rollbackTimeout ?? ''}
            />{' '}
            minutes.
          </div>
        )}
      </div>
    </div>
  );
}

export const awsRollbackClusterStage = {
  key: 'rollbackCluster',
  provides: 'rollbackCluster',
  cloudProvider: 'aws',
  component: AwsRollbackClusterStageConfig,
  validators: [
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerAwsRollbackClusterStage() {
  Registry.pipeline.registerStage(awsRollbackClusterStage);
}

registerAwsRollbackClusterStage();

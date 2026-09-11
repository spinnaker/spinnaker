import React, { useEffect, useState } from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import {
  AccountRegionClusterSelector,
  AccountService,
  PlatformHealthOverride,
  Registry,
  StageConfigField,
  StageConstants,
  TargetSelect,
} from '@spinnaker/core';

export function OracleDisableAsgStageConfig(props: IStageConfigProps) {
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
    if (!stage.target) {
      changes.target = StageConstants.TARGET_LIST[0].val;
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
      <StageConfigField label="Target">
        <TargetSelect
          model={{ target: stage.target }}
          onChange={(target: string) => updateStageField({ target })}
          options={StageConstants.TARGET_LIST}
        />
      </StageConfigField>
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

export const oracleDisableAsgStage = {
  key: 'disableServerGroup',
  provides: 'disableServerGroup',
  cloudProvider: 'oracle',
  component: OracleDisableAsgStageConfig,
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerOracleDisableAsgStage() {
  Registry.pipeline.registerStage(oracleDisableAsgStage);
}

registerOracleDisableAsgStage();

import React, { useEffect, useState } from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import {
  AccountRegionClusterSelector,
  AccountService,
  PlatformHealthOverride,
  StageConfigField,
  StageConstants,
  TargetSelect,
} from '@spinnaker/core';

import { AppengineHealth } from '../../common/appengineHealth';
import type { IAppengineAccount } from '../../domain';

export function getAppengineAccountRegion(accounts: IAppengineAccount[], credentials: string): string {
  return accounts.find((account) => account.name === credentials)?.region;
}

export function initializeAppengineServerGroupStage(stage: any, application: any): void {
  stage.cloudProvider = 'appengine';
  stage.cloudProviderType = 'appengine';

  if (
    stage.isNew &&
    application?.attributes?.platformHealthOnlyShowOverride &&
    application?.attributes?.platformHealthOnly
  ) {
    stage.interestingHealthProviderNames = [AppengineHealth.PLATFORM];
  }
}

type AppengineStageConfigProps = Pick<IStageConfigProps, 'application' | 'pipeline' | 'stage' | 'updateStage'> & {
  showHealthOverride?: boolean;
};

export function AppengineServerGroupStageConfig({
  application,
  pipeline,
  stage,
  updateStage,
  showHealthOverride = false,
}: AppengineStageConfigProps) {
  const [accounts, setAccounts] = useState<IAppengineAccount[]>([]);

  useEffect(() => {
    initializeAppengineServerGroupStage(stage, application);
    AccountService.listAccounts('appengine').then((loadedAccounts: IAppengineAccount[]) => {
      setAccounts(loadedAccounts);
      const credentials = stage.credentials || application?.defaultCredentials?.appengine;
      const region = getAppengineAccountRegion(loadedAccounts, credentials);
      if (credentials || region) {
        stage.credentials = credentials || stage.credentials;
        stage.region = region || stage.region;
        updateStage(stage);
      }
    });
  }, []);

  const onAccountUpdate = (credentials: string) => {
    AccountService.getAccountDetails(credentials).then((accountDetails: IAppengineAccount) => {
      stage.region = accountDetails?.region || stage.region;
      updateStage(stage);
    });
  };

  return (
    <div className="form-horizontal">
      {!pipeline?.strategy && (
        <AccountRegionClusterSelector
          accounts={accounts}
          application={application}
          component={stage}
          disableRegionSelect={true}
          onAccountUpdate={onAccountUpdate}
          onComponentUpdate={updateStage}
          singleRegion="true"
        />
      )}
      <StageConfigField label="Target">
        <TargetSelect
          model={{ target: stage.target }}
          onChange={(target: string) => {
            stage.target = target;
            updateStage(stage);
          }}
          options={StageConstants.TARGET_LIST}
        />
      </StageConfigField>
      {showHealthOverride && application?.attributes?.platformHealthOnlyShowOverride && (
        <PlatformHealthOverride
          interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
          onChange={(interestingHealthProviderNames) => {
            stage.interestingHealthProviderNames = interestingHealthProviderNames;
            updateStage(stage);
          }}
          platformHealthType={AppengineHealth.PLATFORM}
        />
      )}
    </div>
  );
}

export function AppengineServerGroupStageConfigWithHealthOverride(props: AppengineStageConfigProps) {
  return <AppengineServerGroupStageConfig {...props} showHealthOverride={true} />;
}

export function AppengineShrinkClusterStageConfig({
  application,
  pipeline,
  stage,
  updateStage,
}: AppengineStageConfigProps) {
  useEffect(() => {
    initializeAppengineServerGroupStage(stage, application);
    if (stage.shrinkToSize === undefined) {
      stage.shrinkToSize = 1;
    }
    if (stage.allowDeleteActive === undefined) {
      stage.allowDeleteActive = false;
    }
    if (stage.retainLargerOverNewer === undefined) {
      stage.retainLargerOverNewer = 'false';
    }
    stage.retainLargerOverNewer = stage.retainLargerOverNewer.toString();
    updateStage(stage);
  }, []);

  const update = (field: string, value: any) => {
    stage[field] = value;
    updateStage(stage);
  };

  return (
    <div className="form-horizontal">
      <AppengineServerGroupStageConfig
        application={application}
        pipeline={pipeline}
        stage={stage}
        updateStage={updateStage}
      />
      <div className="form-group">
        <label className="col-md-3 sm-label-right">Shrink Options</label>
        <div className="col-md-7 form-inline">
          Shrink to{' '}
          <input
            className="form-control input-sm"
            min={0}
            onChange={(event) => update('shrinkToSize', Number(event.target.value))}
            style={{ width: 60 }}
            type="number"
            value={stage.shrinkToSize || 0}
          />{' '}
          server groups, keeping the{' '}
          <select
            className="form-control input-sm"
            onChange={(event) => update('retainLargerOverNewer', event.target.value)}
            style={{ width: 100 }}
            value={stage.retainLargerOverNewer || 'false'}
          >
            <option value="true">largest</option>
            <option value="false">newest</option>
          </select>
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-offset-3 col-md-7 checkbox">
          <label>
            <input
              checked={!!stage.allowDeleteActive}
              onChange={(event) => update('allowDeleteActive', event.target.checked)}
              type="checkbox"
            />{' '}
            Allow deletion of active server groups
          </label>
        </div>
      </div>
      {application?.attributes?.platformHealthOnlyShowOverride && (
        <PlatformHealthOverride
          interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
          onChange={(interestingHealthProviderNames) => {
            stage.interestingHealthProviderNames = interestingHealthProviderNames;
            updateStage(stage);
          }}
          platformHealthType={AppengineHealth.PLATFORM}
        />
      )}
    </div>
  );
}

import React, { useEffect } from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import { AccountService } from '@spinnaker/core';

import { AppengineHealth } from '../../common/appengineHealth';
import type { IAppengineAccount } from '../../domain';

export function getAppengineAccountRegion(accounts: IAppengineAccount[], credentials: string): string {
  return accounts.find((account) => account.name === credentials)?.region;
}

export function initializeAppengineServerGroupStage(stage: any, application: any): void {
  stage.cloudProvider = 'appengine';

  if (
    stage.isNew &&
    application?.attributes?.platformHealthOnlyShowOverride &&
    application?.attributes?.platformHealthOnly
  ) {
    stage.interestingHealthProviderNames = [AppengineHealth.PLATFORM];
  }
}

type AppengineStageConfigProps = Pick<IStageConfigProps, 'application' | 'stage' | 'updateStage'>;

export function AppengineServerGroupStageConfig({ application, stage, updateStage }: AppengineStageConfigProps) {
  useEffect(() => {
    initializeAppengineServerGroupStage(stage, application);
    AccountService.listAccounts('appengine').then((accounts: IAppengineAccount[]) => {
      const credentials = stage.credentials || application?.defaultCredentials?.appengine;
      const region = getAppengineAccountRegion(accounts, credentials);
      if (credentials || region) {
        stage.credentials = credentials || stage.credentials;
        stage.region = region || stage.region;
        updateStage(stage);
      }
    });
  }, []);

  const update = (field: string, value: any) => {
    stage[field] = value;
    updateStage(stage);
  };

  const updateCredentials = (credentials: string) => {
    stage.credentials = credentials;
    AccountService.getAccountDetails(credentials).then((accountDetails: IAppengineAccount) => {
      stage.region = accountDetails.region || stage.region;
      updateStage(stage);
    });
    updateStage(stage);
  };

  return (
    <div className="form-horizontal">
      <div className="form-group">
        <label className="col-md-3 sm-label-right">Account</label>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            onChange={(event) => updateCredentials(event.target.value)}
            value={stage.credentials || ''}
          />
        </div>
      </div>
      <div className="form-group">
        <label className="col-md-3 sm-label-right">Cluster</label>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            onChange={(event) => update('cluster', event.target.value)}
            value={stage.cluster || ''}
          />
        </div>
      </div>
      <div className="form-group">
        <label className="col-md-3 sm-label-right">Target</label>
        <div className="col-md-7">
          <select
            className="form-control input-sm"
            onChange={(event) => update('target', event.target.value)}
            value={stage.target || ''}
          >
            <option value="">Select...</option>
            <option value="current_asg_dynamic">Current Server Group</option>
            <option value="ancestor_asg_dynamic">Previous Server Group</option>
            <option value="oldest_asg_dynamic">Oldest Server Group</option>
          </select>
        </div>
      </div>
    </div>
  );
}

export function AppengineShrinkClusterStageConfig({ application, stage, updateStage }: AppengineStageConfigProps) {
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
      <AppengineServerGroupStageConfig application={application} stage={stage} updateStage={updateStage} />
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
    </div>
  );
}

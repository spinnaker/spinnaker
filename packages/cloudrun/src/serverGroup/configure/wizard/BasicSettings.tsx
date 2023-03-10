import type { FormikProps } from 'formik';
import React from 'react';

import type { Application, IAccount, IServerGroup } from '@spinnaker/core';
import { AccountSelectInput, HelpField, NameUtils, ReactInjector, ServerGroupNamePreview } from '@spinnaker/core';

import type { ICloudrunServerGroupCommandData } from '../serverGroupCommandBuilder.service';

export interface IServerGroupBasicSettingsProps {
  accounts: IAccount[];
  onAccountSelect: (account: string) => void;
  selectedAccount: string;
  formik: IWizardServerGroupBasicSettingsProps['formik'];
  onEnterStack: (stack: string) => void;
  detailsChanged: (detail: string) => void;
  app: Application;
}

export interface IServerGroupBasicSettingsState {
  namePreview: string;
  createsNewCluster: boolean;
  latestServerGroup: IServerGroup;
}

export function ServerGroupBasicSettings({
  accounts,
  onAccountSelect,
  selectedAccount,
  formik,
  onEnterStack,
  detailsChanged,
  app,
}: IServerGroupBasicSettingsProps) {
  const { values } = formik;
  const { stack = '', freeFormDetails } = values;

  const namePreview = NameUtils.getClusterName(app.name, stack, freeFormDetails);
  const createsNewCluster = !app.clusters.find((c) => c.name === namePreview);
  const inCluster = (app.serverGroups.data as IServerGroup[])
    .filter((serverGroup) => {
      return (
        serverGroup.cluster === namePreview &&
        serverGroup.account === values.command.credentials &&
        serverGroup.region === values.command.region
      );
    })
    .sort((a, b) => a.createdTime - b.createdTime);
  const latestServerGroup = inCluster.length ? inCluster.pop() : null;

  const navigateToLatestServerGroup = () => {
    const { values } = formik;
    const params = {
      provider: values.command.selectedProvider,
      accountId: latestServerGroup.account,
      region: latestServerGroup.region,
      serverGroup: latestServerGroup.name,
    };

    const { $state } = ReactInjector;
    if ($state.is('home.applications.application.insight.clusters')) {
      $state.go('.serverGroup', params);
    } else {
      $state.go('^.serverGroup', params);
    }
  };

  return (
    <div className="form-horizontal">
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Account</div>
        <div className="col-md-7">
          <AccountSelectInput
            value={selectedAccount}
            onChange={(evt: any) => onAccountSelect(evt.target.value)}
            readOnly={false}
            accounts={accounts}
            provider="cloudrun"
          />
        </div>
      </div>

      <div className="form-group">
        <div className="col-md-3 sm-label-right">
          Stack <HelpField id="cloudrun.serverGroup.stack" />
        </div>
        <div className="col-md-7">
          <input
            type="text"
            className="form-control input-sm no-spel"
            value={stack}
            onChange={(e) => onEnterStack(e.target.value)}
          />
        </div>
      </div>

      <div className="form-group">
        <div className="col-md-3 sm-label-right">
          Detail <HelpField id="cloudrun.serverGroup.detail" />
        </div>
        <div className="col-md-7">
          <input
            type="text"
            className="form-control input-sm no-spel"
            value={freeFormDetails}
            onChange={(e) => detailsChanged(e.target.value)}
          />
        </div>
      </div>
      {!values.command.viewState.hideClusterNamePreview && (
        <ServerGroupNamePreview
          createsNewCluster={createsNewCluster}
          latestServerGroupName={latestServerGroup?.name}
          mode={values.command.viewState.mode}
          namePreview={namePreview}
          navigateToLatestServerGroup={navigateToLatestServerGroup}
        />
      )}
    </div>
  );
}

export interface IWizardServerGroupBasicSettingsProps {
  formik: FormikProps<ICloudrunServerGroupCommandData>;
  app: Application;
}

export class WizardServerGroupBasicSettings extends React.Component<IWizardServerGroupBasicSettingsProps> {
  private accountUpdated = (account: string): void => {
    const { formik } = this.props;
    formik.values.command.account = account;
    formik.setFieldValue('account', account);
  };

  private stackChanged = (stack: string): void => {
    const { setFieldValue, values } = this.props.formik;
    values.command.stack = stack;
    setFieldValue('stack', stack);
  };

  private freeFormDetailsChanged = (freeFormDetails: string) => {
    const { setFieldValue, values } = this.props.formik;
    values.command.freeFormDetails = freeFormDetails;
    setFieldValue('freeFormDetails', freeFormDetails);
  };

  public render() {
    const { formik, app } = this.props;
    return (
      <ServerGroupBasicSettings
        accounts={formik.values.metadata?.backingData?.accounts || []}
        onAccountSelect={this.accountUpdated}
        selectedAccount={formik.values.command?.account || ''}
        onEnterStack={this.stackChanged}
        formik={formik}
        detailsChanged={this.freeFormDetailsChanged}
        app={app}
      />
    );
  }
}

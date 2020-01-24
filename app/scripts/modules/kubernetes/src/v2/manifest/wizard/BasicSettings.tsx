import React from 'react';
import { FormikProps } from 'formik';

import { AccountSelectInput, Application, HelpField, IAccount } from '@spinnaker/core';

import { IKubernetesManifestCommandData } from '../manifestCommandBuilder.service';

export interface IManifestBasicSettingsProps {
  app: Application;
  accounts: IAccount[];
  onAccountSelect: (account: string) => void;
  selectedAccount: string;
}

export function ManifestBasicSettings({
  app,
  accounts,
  onAccountSelect,
  selectedAccount,
}: IManifestBasicSettingsProps) {
  return (
    <div className="form-horizontal">
      <div className="form-group">
        <div className="col-md-3 sm-label-right">
          Account <HelpField id="kubernetes.manifest.account" />
        </div>
        <div className="col-md-7">
          <AccountSelectInput
            value={selectedAccount}
            onChange={(evt: any) => onAccountSelect(evt.target.value)}
            readOnly={false}
            accounts={accounts}
            provider="kubernetes"
          />
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-3 sm-label-right">
          Application <HelpField id="kubernetes.manifest.application" />
        </div>
        <div className="col-md-7">
          <input type="text" className="form-control input-sm no-spel" readOnly={true} value={app.name} />
        </div>
      </div>
    </div>
  );
}

export interface IWizardManifestBasicSettingsProps {
  app: Application;
  formik: FormikProps<IKubernetesManifestCommandData>;
}

export class WizardManifestBasicSettings extends React.Component<IWizardManifestBasicSettingsProps> {
  private accountUpdated = (account: string): void => {
    const { formik } = this.props;
    formik.values.command.account = account;
    formik.setFieldValue('account', account);
  };

  public render() {
    const { formik, app } = this.props;
    return (
      <ManifestBasicSettings
        app={app}
        accounts={formik.values.metadata.backingData.accounts}
        onAccountSelect={this.accountUpdated}
        selectedAccount={formik.values.command.account}
      />
    );
  }
}

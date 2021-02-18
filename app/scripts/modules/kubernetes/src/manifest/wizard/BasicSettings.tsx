import { FormikProps } from 'formik';
import React from 'react';

import { AccountSelectInput, HelpField, IAccount } from '@spinnaker/core';

import { IKubernetesManifestCommandData } from '../manifestCommandBuilder.service';

export interface IManifestBasicSettingsProps {
  accounts: IAccount[];
  onAccountSelect: (account: string) => void;
  selectedAccount: string;
}

export function ManifestBasicSettings({ accounts, onAccountSelect, selectedAccount }: IManifestBasicSettingsProps) {
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
    </div>
  );
}

export interface IWizardManifestBasicSettingsProps {
  formik: FormikProps<IKubernetesManifestCommandData>;
}

export class WizardManifestBasicSettings extends React.Component<IWizardManifestBasicSettingsProps> {
  private accountUpdated = (account: string): void => {
    const { formik } = this.props;
    formik.values.command.account = account;
    formik.setFieldValue('account', account);
  };

  public render() {
    const { formik } = this.props;
    return (
      <ManifestBasicSettings
        accounts={formik.values.metadata.backingData.accounts}
        onAccountSelect={this.accountUpdated}
        selectedAccount={formik.values.command.account}
      />
    );
  }
}

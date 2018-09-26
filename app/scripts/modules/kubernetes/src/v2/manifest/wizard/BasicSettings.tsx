import * as React from 'react';
import { FormikErrors } from 'formik';

import { NgReact, HelpField, IWizardPageProps, wizardPage, Application } from '@spinnaker/core';

import { IKubernetesManifestCommandData } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

export interface IManifestBasicSettingsProps extends IWizardPageProps<IKubernetesManifestCommandData> {
  app: Application;
}

class ManifestBasicSettingsImpl extends React.Component<IManifestBasicSettingsProps> {
  public static LABEL = 'Basic Settings';

  private accountUpdated = (account: string): void => {
    const { formik } = this.props;
    formik.values.command.account = account;
    formik.setFieldValue('account', account);
  };

  public validate = (_formik: IKubernetesManifestCommandData) => {
    return {} as FormikErrors<IKubernetesManifestCommandData>;
  };

  public render() {
    const { formik, app } = this.props;
    const { AccountSelectField } = NgReact;

    const accounts = formik.values.metadata.backingData.accounts;

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Account <HelpField id="kubernetes.manifest.account" />
          </div>
          <div className="col-md-7">
            <AccountSelectField
              readOnly={false}
              component={formik.values.command}
              field="account"
              accounts={accounts}
              provider="kubernetes"
              onChange={this.accountUpdated}
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
}

export const ManifestBasicSettings = wizardPage<IManifestBasicSettingsProps>(ManifestBasicSettingsImpl);

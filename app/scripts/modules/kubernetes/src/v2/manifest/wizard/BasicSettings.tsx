import * as React from 'react';
import { FormikErrors, FormikProps, FormikValues } from 'formik';

import { NgReact, HelpField, IWizardPageProps, wizardPage, Application } from '@spinnaker/core';

import { IKubernetesManifestCommandData } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

export interface IManifestBasicSettingsProps {
  app: Application;
}

class ManifestBasicSettingsImpl extends React.Component<
  IManifestBasicSettingsProps & IWizardPageProps & FormikProps<IKubernetesManifestCommandData>
> {
  public static LABEL = 'Basic Settings';

  constructor(props: IManifestBasicSettingsProps & IWizardPageProps & FormikProps<IKubernetesManifestCommandData>) {
    super(props);
  }

  private accountUpdated = (account: string): void => {
    const { setFieldValue, values } = this.props;
    values.command.account = account;
    setFieldValue('account', account);
  };

  public validate = (_values: FormikValues): FormikErrors<IKubernetesManifestCommandData> => {
    const errors = {} as FormikErrors<IKubernetesManifestCommandData>;
    return errors;
  };

  public render() {
    const { values, app } = this.props;
    const { AccountSelectField } = NgReact;

    const accounts = values.metadata.backingData.accounts;

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Account <HelpField id="kubernetes.manifest.account" />
          </div>
          <div className="col-md-7">
            <AccountSelectField
              readOnly={false}
              component={values.command}
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

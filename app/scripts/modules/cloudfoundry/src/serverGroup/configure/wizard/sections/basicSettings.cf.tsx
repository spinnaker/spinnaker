import * as React from 'react';

import { Field, FormikErrors } from 'formik';

import {
  AccountSelectField,
  AccountService,
  IAccount,
  IRegion,
  IWizardPageProps,
  wizardPage,
  HelpField,
  RegionSelectField,
  ValidationMessage,
} from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from '../../serverGroupConfigurationModel.cf';
import { CloudFoundryDeploymentStrategySelector } from 'cloudfoundry/deploymentStrategy/DeploymentStrategySelector';

export type ICloudFoundryServerGroupBasicSettingsProps = IWizardPageProps<ICloudFoundryCreateServerGroupCommand>;

export interface ICloudFoundryServerGroupLocationSettingsState {
  accounts: IAccount[];
  regions: IRegion[];
}

class BasicSettingsImpl extends React.Component<
  ICloudFoundryServerGroupBasicSettingsProps,
  ICloudFoundryServerGroupLocationSettingsState
> {
  public static get LABEL() {
    return 'Basic Settings';
  }

  public state: ICloudFoundryServerGroupLocationSettingsState = {
    accounts: [],
    regions: [],
  };

  public componentDidMount(): void {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts: accounts });
      this.accountChanged();
    });
  }

  private accountUpdated = (account: string): void => {
    this.props.formik.values.credentials = account;
    this.props.formik.setFieldValue('credentials', account);
    this.accountChanged();
  };

  private accountChanged = (): void => {
    const { credentials } = this.props.formik.values;
    if (credentials) {
      AccountService.getRegionsForAccount(credentials).then(regions => {
        this.setState({ regions: regions });
      });
    }
  };

  private regionUpdated = (region: string): void => {
    this.props.formik.values.region = region;
    this.props.formik.setFieldValue('region', region);
  };

  private strategyChanged = (_values: ICloudFoundryCreateServerGroupCommand, strategy: any) => {
    this.props.formik.setFieldValue('strategy', strategy.key);
  };

  private onStrategyFieldChange = (key: string, value: any) => {
    this.props.formik.setFieldValue(key, value);
  };

  public render(): JSX.Element {
    const { accounts, regions } = this.state;
    const { values, errors } = this.props.formik;
    return (
      <div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Account</div>
          <div className="col-md-7">
            <AccountSelectField
              component={values}
              field="credentials"
              accounts={accounts}
              provider="cloudfoundry"
              onChange={this.accountUpdated}
            />
          </div>
        </div>
        <RegionSelectField
          labelColumns={3}
          component={values}
          field="region"
          account={values.credentials}
          onChange={this.regionUpdated}
          regions={regions}
        />
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Stack <HelpField id="cf.serverGroup.stack" />
          </div>
          <div className="col-md-7">
            <Field className="form-control input-sm" type="text" name="stack" />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Detail <HelpField id="cf.serverGroup.detail" />
          </div>
          <div className="col-md-7">
            <Field className="form-control input-sm" type="text" name="freeFormDetails" />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Start on creation <HelpField id="cf.serverGroup.startApplication" />
          </div>
          <div className="checkbox checkbox-inline">
            <Field type="checkbox" name="startApplication" checked={values.startApplication} />
          </div>
        </div>
        {(values.viewState.mode === 'editPipeline' || values.viewState.mode === 'createPipeline') && (
          <CloudFoundryDeploymentStrategySelector
            onFieldChange={this.onStrategyFieldChange}
            onStrategyChange={this.strategyChanged}
            command={values}
          />
        )}
        {errors.credentials && (
          <div className="wizard-pod-row-errors">
            <ValidationMessage message={errors.credentials} type={'error'} />
          </div>
        )}
        {errors.region && (
          <div className="wizard-pod-row-errors">
            <ValidationMessage message={errors.region} type={'error'} />
          </div>
        )}
        {errors.stack && (
          <div className="wizard-pod-row-errors">
            <ValidationMessage message={errors.stack} type={'error'} />
          </div>
        )}
        {errors.freeFormDetails && (
          <div className="wizard-pod-row-errors">
            <ValidationMessage message={errors.freeFormDetails} type={'error'} />
          </div>
        )}
      </div>
    );
  }

  public validate(values: ICloudFoundryCreateServerGroupCommand) {
    const errors = {} as FormikErrors<ICloudFoundryCreateServerGroupCommand>;

    if (!values.credentials) {
      errors.credentials = 'Account must be selected';
    }
    if (!values.region) {
      errors.region = 'Region must be selected';
    }
    if (values.stack && !values.stack.match(/^[a-zA-Z0-9]*$/)) {
      errors.stack = 'Stack can only contain letters and numbers.';
    }
    if (values.freeFormDetails && !values.freeFormDetails.match(/^[a-zA-Z0-9-]*$/)) {
      errors.freeFormDetails = 'Detail can only contain letters, numbers, and dashes.';
    }

    return errors;
  }
}

export const CloudFoundryServerGroupBasicSettings = wizardPage(BasicSettingsImpl);

import * as React from 'react';

import { get } from 'lodash';

import { FormikErrors, FormikProps } from 'formik';

import {
  AccountService,
  CheckboxInput,
  FormikFormField,
  IAccount,
  IRegion,
  IWizardPageComponent,
  HelpField,
  ReactSelectInput,
  TextInput,
} from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from 'cloudfoundry/serverGroup/configure/serverGroupConfigurationModel.cf';
import { CloudFoundryDeploymentStrategySelector } from 'cloudfoundry/deploymentStrategy/CloudFoundryDeploymentStrategySelector';

import 'cloudfoundry/common/cloudFoundry.less';

export interface ICloudFoundryServerGroupBasicSettingsProps {
  formik: FormikProps<ICloudFoundryCreateServerGroupCommand>;
  isPipelineClone: boolean;
}

export interface ICloudFoundryServerGroupLocationSettingsState {
  accounts: IAccount[];
  regions: IRegion[];
}

export class CloudFoundryServerGroupBasicSettings
  extends React.Component<ICloudFoundryServerGroupBasicSettingsProps, ICloudFoundryServerGroupLocationSettingsState>
  implements IWizardPageComponent<ICloudFoundryCreateServerGroupCommand> {
  public state: ICloudFoundryServerGroupLocationSettingsState = {
    accounts: [],
    regions: [],
  };

  public componentDidMount(): void {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts });
      this.updateRegionList();
    });
  }

  private accountChanged = (): void => {
    this.updateRegionList();
    const regionField = this.props.isPipelineClone ? 'destination.region' : 'region';
    this.props.formik.setFieldValue(regionField, '');
  };

  private updateRegionList = (): void => {
    const accountField = this.props.isPipelineClone ? 'destination.account' : 'credentials';
    const credentials = get(this.props.formik.values, accountField, undefined);
    if (credentials) {
      AccountService.getRegionsForAccount(credentials).then(regions => {
        this.setState({ regions: regions });
      });
    }
  };

  private strategyChanged = (_values: ICloudFoundryCreateServerGroupCommand, strategy: any) => {
    this.props.formik.setFieldValue('strategy', strategy.key);
  };

  private onStrategyFieldChange = (key: string, value: any) => {
    this.props.formik.setFieldValue(key, value);
  };

  public render(): JSX.Element {
    const { formik, isPipelineClone } = this.props;
    const { accounts, regions } = this.state;
    const { values } = formik;
    const accountField = isPipelineClone ? 'destination.account' : 'credentials';
    const regionField = isPipelineClone ? 'destination.region' : 'region';
    return (
      <div className="form-group">
        <div className="col-md-11">
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name={accountField}
              label="Account"
              fastField={false}
              input={props => (
                <ReactSelectInput
                  inputClassName="cloudfoundry-react-select"
                  {...props}
                  stringOptions={accounts && accounts.map((acc: IAccount) => acc.name)}
                  clearable={false}
                />
              )}
              onChange={this.accountChanged}
              required={true}
            />
          </div>
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name={regionField}
              label="Region"
              fastField={false}
              input={props => (
                <ReactSelectInput
                  {...props}
                  stringOptions={regions && regions.map((region: IRegion) => region.name)}
                  inputClassName={'cloudfoundry-react-select'}
                  clearable={false}
                />
              )}
              required={true}
            />
          </div>
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name="stack"
              label="Stack"
              input={props => <TextInput {...props} />}
              help={<HelpField id="cf.serverGroup.stack" />}
            />
          </div>
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name="freeFormDetails"
              label="Detail"
              input={props => <TextInput {...props} />}
              help={<HelpField id="cf.serverGroup.detail" />}
            />
          </div>
          <div className="sp-margin-m-bottom cloud-foundry-checkbox">
            <FormikFormField
              name="startApplication"
              label="Start on creation"
              fastField={false}
              input={props => <CheckboxInput {...props} />}
              help={<HelpField id="cf.serverGroup.startApplication" />}
            />
          </div>
          {(values.viewState.mode === 'editPipeline' || values.viewState.mode === 'createPipeline') && (
            <CloudFoundryDeploymentStrategySelector
              onFieldChange={this.onStrategyFieldChange}
              onStrategyChange={this.strategyChanged}
              command={values}
            />
          )}
        </div>
      </div>
    );
  }

  public validate(values: ICloudFoundryCreateServerGroupCommand) {
    const errors = {} as FormikErrors<ICloudFoundryCreateServerGroupCommand>;

    if (values.stack && !values.stack.match(/^[a-zA-Z0-9]*$/)) {
      errors.stack = 'Stack can only contain letters and numbers.';
    }
    if (values.freeFormDetails && !values.freeFormDetails.match(/^[a-zA-Z0-9-]*$/)) {
      errors.freeFormDetails = 'Detail can only contain letters, numbers, and dashes.';
    }

    return errors;
  }
}

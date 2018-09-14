import * as React from 'react';

import { FormikErrors, FormikProps } from 'formik';
import Select, { Option } from 'react-select';

import {
  AccountService,
  IAccount,
  IDeploymentStrategy,
  IRegion,
  IWizardPageProps,
  NgReact,
  wizardPage,
  HelpField,
  RegionSelectField,
  ValidationMessage,
} from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from '../../serverGroupConfigurationModel.cf';

export interface ICloudFoundryServerGroupBasicSettingsProps {
  credentials: string;
  region: string;
  stack: string;
  freeFormDetails: string;
  strategy?: string;
  viewState: { mode: string };
}

export interface ICloudFoundryServerGroupLocationSettingsState {
  accounts: IAccount[];
  regions: IRegion[];
}

class BasicSettingsImpl extends React.Component<
  ICloudFoundryServerGroupBasicSettingsProps & IWizardPageProps & FormikProps<ICloudFoundryCreateServerGroupCommand>,
  ICloudFoundryServerGroupLocationSettingsState
> {
  private deploymentStrategies: IDeploymentStrategy[] = [
    {
      label: 'Red/Black',
      description:
        'Disables <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
      key: 'redblack',
    },
    {
      label: 'Highlander',
      description:
        'Destroys <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
      key: 'highlander',
    },
    {
      label: 'None',
      description: 'Creates the next server group with no impact on existing server groups',
      key: '',
      providerRestricted: false,
    },
  ];
  public static get LABEL() {
    return 'Basic Settings';
  }

  constructor(
    props: ICloudFoundryServerGroupBasicSettingsProps &
      IWizardPageProps &
      FormikProps<ICloudFoundryCreateServerGroupCommand>,
  ) {
    super(props);
    this.state = {
      accounts: [],
      regions: [],
    };
  }

  public componentDidMount(): void {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts: accounts });
      this.accountChanged();
    });
  }

  private accountUpdated = (account: string): void => {
    this.props.values.credentials = account;
    this.props.setFieldValue('credentials', account);
    this.accountChanged();
  };

  private accountChanged = (): void => {
    if (this.props.values.credentials) {
      AccountService.getRegionsForAccount(this.props.values.credentials).then(regions => {
        this.setState({ regions: regions });
      });
    }
  };

  private regionUpdated = (region: string): void => {
    this.props.values.region = region;
    this.props.setFieldValue('region', region);
  };

  private stackUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const stack = event.target.value;
    this.props.values.stack = stack;
    this.props.setFieldValue('stack', stack);
  };

  private detailUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const freeFormDetails = event.target.value;
    this.props.values.freeFormDetails = freeFormDetails;
    this.props.setFieldValue('freeFormDetails', freeFormDetails);
  };

  private deploymentStrategyUpdated = (option: Option<string>): void => {
    this.props.values.strategy = option.value;
    this.props.setFieldValue('strategy', option.value);
  };

  public render(): JSX.Element {
    const { accounts, regions } = this.state;
    const { values, errors } = this.props;
    const { AccountSelectField } = NgReact;
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
            <input className="form-control input-sm" type="text" value={values.stack} onChange={this.stackUpdated} />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Detail <HelpField id="cf.serverGroup.detail" />
          </div>
          <div className="col-md-7">
            <input
              className="form-control input-sm"
              type="text"
              value={values.freeFormDetails}
              onChange={this.detailUpdated}
            />
          </div>
        </div>
        {(values.viewState.mode === 'editPipeline' || values.viewState.mode === 'createPipeline') && (
          <div className="form-group row">
            <label className="col-md-3 sm-label-right">Deployment Strategy</label>
            <div className="col-md-7">
              <Select
                options={this.deploymentStrategies.map((deploymentStrategy: IDeploymentStrategy) => ({
                  label: deploymentStrategy.label,
                  value: deploymentStrategy.key,
                }))}
                clearable={false}
                value={values.strategy}
                onChange={this.deploymentStrategyUpdated}
              />
            </div>
          </div>
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

  public validate(
    values: ICloudFoundryServerGroupBasicSettingsProps,
  ): FormikErrors<ICloudFoundryCreateServerGroupCommand> {
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

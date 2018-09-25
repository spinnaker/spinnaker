import * as React from 'react';

import { FormikErrors } from 'formik';

import {
  AccountService,
  Application,
  IAccount,
  IWizardPageProps,
  wizardPage,
  NgReact,
  RegionSelectField,
  IRegion,
} from '@spinnaker/core';

import { ICloudFoundryAccount, ICloudFoundryDomain, ICloudFoundryLoadBalancerUpsertCommand } from 'cloudfoundry/domain';
import { RouteDomainSelectField } from 'cloudfoundry/routeDomains';

export interface ILoadBalancerDetailsProps extends IWizardPageProps<ICloudFoundryLoadBalancerUpsertCommand> {
  app: Application;
  isNew?: boolean;
}

export interface ILoadBalancerDetailsState {
  accounts: IAccount[];
  availabilityZones: string[];
  existingLoadBalancerNames: string[];
  domains: ICloudFoundryDomain[];
  regions: IRegion[];
}

class LoadBalancerDetailsImpl extends React.Component<ILoadBalancerDetailsProps, ILoadBalancerDetailsState> {
  public static LABEL = 'Details';

  public state: ILoadBalancerDetailsState = {
    accounts: undefined,
    availabilityZones: [],
    existingLoadBalancerNames: [],
    domains: [],
    regions: [],
  };

  public validate(values: ICloudFoundryLoadBalancerUpsertCommand) {
    const errors = {} as FormikErrors<ICloudFoundryLoadBalancerUpsertCommand>;
    if (!values.host || !values.host.match(/^[a-zA-Z0-9-]*$/)) {
      errors.host = 'Host name can only contain letters, numbers, and dashes';
    }
    if (!values.credentials) {
      errors.credentials = 'Account must be selected';
    }
    if (!values.region) {
      errors.region = 'Region must be selected';
    }
    if (!values.domain) {
      errors.domain = 'Domain must be selected';
    }
    if (values.port && !values.port.match(/^[0-9]*$/)) {
      errors.port = 'Port can only be a numeric value';
    }
    if (values.path && !values.path.match(/^[a-zA-Z0-9-\\]*$/)) {
      errors.path = 'Path must be in the format of "abcd/bcef/xyz"';
    }
    return errors;
  }

  public componentDidMount(): void {
    this.loadAccounts();
  }

  private loadAccounts(): void {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts });
      this.accountUpdated(this.props.formik.values.credentials);
    });
  }

  private accountUpdated = (account?: string): void => {
    this.props.formik.setFieldValue('credentials', account);
    if (account) {
      AccountService.getAccountDetails(account).then((accountDetails: ICloudFoundryAccount) => {
        this.setState({ domains: accountDetails.domains });
      });
      AccountService.getRegionsForAccount(account).then(regions => {
        this.setState({ regions });
      });
    }
  };

  private regionUpdated = (region: string): void => {
    this.props.formik.setFieldValue('region', region);
    if (region) {
      const { credentials } = this.props.formik.values;
      AccountService.getAccountDetails(credentials).then((accountDetails: ICloudFoundryAccount) => {
        const { domains } = accountDetails;
        this.setState({
          domains: domains.filter(
            domain => domain.organization === undefined || region.match('^' + domain.organization.name),
          ),
        });
      });
    }
  };

  private domainUpdated = (domainName: string): void => {
    this.props.formik.setFieldValue('domainName', domainName);
  };

  private hostChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const host = event.target.value;
    this.props.formik.values.host = host;
    this.props.formik.setFieldValue('host', host);
  };

  private portChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const port = event.target.value;
    this.props.formik.values.port = port;
    this.props.formik.setFieldValue('port', port);
  };

  private pathChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const path = event.target.value;
    this.props.formik.values.path = path;
    this.props.formik.setFieldValue('path', path);
  };

  public render() {
    const { values } = this.props.formik;
    const { accounts, domains, regions } = this.state;
    const { AccountSelectField } = NgReact;
    return (
      <div className="container-fluid form-horizontal">
        <div className="modal-body">
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
          <RouteDomainSelectField
            labelColumns={3}
            component={values}
            field="domain"
            account={values.credentials}
            onChange={this.domainUpdated}
            domains={domains}
          />
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Host</div>
            <div className="col-md-7">
              <input
                className="form-control input-sm target-group-name"
                type="text"
                value={values.host}
                name="host"
                required={true}
                onChange={this.hostChanged}
              />
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Port</div>
            <div className="col-md-3">
              <input
                className="form-control input-sm target-group-name"
                type="text"
                value={values.port}
                required={false}
                name="port"
                onChange={this.portChanged}
              />
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Path</div>
            <div className="col-md-7">
              <input
                className="form-control input-sm target-group-name"
                type="text"
                value={values.path}
                required={false}
                name="path"
                onChange={this.pathChanged}
              />
            </div>
          </div>
        </div>
      </div>
    );
  }
}

export const LoadBalancerDetails = wizardPage<ILoadBalancerDetailsProps>(LoadBalancerDetailsImpl);

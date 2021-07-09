import { FormikErrors, FormikProps } from 'formik';
import React from 'react';
import Select, { Option } from 'react-select';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { AccountService, Application, IAccount, IRegion, IWizardPageComponent } from '@spinnaker/core';
import { ICloudFoundryAccount, ICloudFoundryDomain, ICloudFoundryLoadBalancerUpsertCommand } from '../../domain';
import { RouteDomainSelectField } from '../../routeDomains';

export interface ILoadBalancerDetailsProps {
  app: Application;
  formik: FormikProps<ICloudFoundryLoadBalancerUpsertCommand>;
  isNew?: boolean;
}

export interface ILoadBalancerDetailsState {
  accounts: IAccount[];
  availabilityZones: string[];
  existingLoadBalancerNames: string[];
  domains: ICloudFoundryDomain[];
  regions: IRegion[];
}

export class LoadBalancerDetails
  extends React.Component<ILoadBalancerDetailsProps, ILoadBalancerDetailsState>
  implements IWizardPageComponent<ICloudFoundryLoadBalancerUpsertCommand> {
  private destroy$ = new Subject();
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

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private loadAccounts(): void {
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((accounts) => {
        this.setState({ accounts });
        this.loadDomainsAndRegions();
      });
  }

  private accountUpdated = (option: Option<string>): void => {
    const account = option.value;
    this.props.formik.setFieldValue('credentials', account);
    this.loadDomainsAndRegions();
  };

  private loadDomainsAndRegions(): void {
    const account = this.props.formik.values.credentials;
    if (account) {
      observableFrom(AccountService.getAccountDetails(account))
        .pipe(takeUntil(this.destroy$))
        .subscribe((accountDetails: ICloudFoundryAccount) => this.setState({ domains: accountDetails.domains }));
      observableFrom(AccountService.getRegionsForAccount(account))
        .pipe(takeUntil(this.destroy$))
        .subscribe((regions) => this.setState({ regions }));
    }
  }

  private regionUpdated = (option: Option<string>): void => {
    const region = option.value;
    this.props.formik.setFieldValue('region', region);
    if (region) {
      const { credentials } = this.props.formik.values;
      observableFrom(AccountService.getAccountDetails(credentials))
        .pipe(takeUntil(this.destroy$))
        .subscribe((accountDetails: ICloudFoundryAccount) => {
          const { domains } = accountDetails;
          this.setState({
            domains: domains.filter(
              (domain) => domain.organization === undefined || region.match('^' + domain.organization.name),
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
    return (
      <div className="container-fluid form-horizontal">
        <div className="modal-body">
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Account</div>
            <div className="col-md-7">
              <Select
                options={
                  accounts &&
                  accounts.map((acc: IAccount) => ({
                    label: acc.name,
                    value: acc.name,
                  }))
                }
                clearable={false}
                value={values.credentials}
                onChange={this.accountUpdated}
              />
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Region</div>
            <div className="col-md-7">
              <Select
                options={
                  regions &&
                  regions.map((region: IRegion) => ({
                    label: region.name,
                    value: region.name,
                  }))
                }
                clearable={false}
                value={values.region}
                onChange={this.regionUpdated}
              />
            </div>
          </div>
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

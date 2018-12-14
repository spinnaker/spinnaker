import * as React from 'react';
import * as classNames from 'classnames';
import { IPromise } from 'angular';
import { chain, find, isEqual, isNil, trimEnd, uniq } from 'lodash';
import { Field, FormikErrors } from 'formik';

import {
  AccountSelectInput,
  AccountService,
  Application,
  HelpField,
  IAccount,
  IMoniker,
  IRegion,
  ISubnet,
  IWizardPageProps,
  NameUtils,
  RegionSelectField,
  Spinner,
  SubnetReader,
  ValidationMessage,
  wizardPage,
} from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import { IAmazonLoadBalancer, IAmazonLoadBalancerUpsertCommand } from 'amazon/domain';
import { AvailabilityZoneSelector } from 'amazon/serverGroup/AvailabilityZoneSelector';
import { SubnetSelectField } from 'amazon/subnet';

export interface ISubnetOption {
  availabilityZones: string[];
  deprecated?: boolean;
  label: string;
  purpose: string;
  vpcIds: string[];
}

export interface ILoadBalancerLocationProps extends IWizardPageProps<IAmazonLoadBalancerUpsertCommand> {
  app: Application;
  forPipelineConfig?: boolean;
  isNew?: boolean;
  loadBalancer?: IAmazonLoadBalancer;
}

export interface ILoadBalancerLocationState {
  accounts: IAccount[];
  availabilityZones: string[];
  existingLoadBalancerNames: string[];
  hideInternalFlag: boolean;
  internalFlagToggled: boolean;
  regions: IRegion[];
  subnets: ISubnetOption[];
}

class LoadBalancerLocationImpl extends React.Component<ILoadBalancerLocationProps, ILoadBalancerLocationState> {
  public static LABEL = 'Location';

  public state: ILoadBalancerLocationState = {
    accounts: undefined,
    availabilityZones: [],
    existingLoadBalancerNames: [],
    hideInternalFlag: false,
    internalFlagToggled: false,
    regions: [],
    subnets: [],
  };

  public validate(values: IAmazonLoadBalancerUpsertCommand) {
    const errors = {} as FormikErrors<IAmazonLoadBalancerUpsertCommand>;

    if (this.state.existingLoadBalancerNames.includes(values.name)) {
      errors.name = `There is already a load balancer in ${values.credentials}:${values.region} with that name.`;
    }

    if (values.name && values.name.length > 32) {
      errors.name = 'Load balancer names cannot exceed 32 characters in length';
    }

    if (values.stack && !values.stack.match(/^[a-zA-Z0-9]*$/)) {
      errors.stack = 'Stack can only contain letters and numbers.';
    }

    if (values.detail && !values.detail.match(/^[a-zA-Z0-9-]*$/)) {
      errors.detail = 'Detail can only contain letters, numbers, and dashes.';
    }

    return errors;
  }

  protected buildName(): void {
    const { values } = this.props.formik;
    if (isNil(values.moniker)) {
      const nameParts = NameUtils.parseLoadBalancerName(values.name);
      values.stack = nameParts.stack;
      values.detail = nameParts.freeFormDetails;
    } else {
      values.stack = values.moniker.stack;
      values.detail = values.moniker.detail;
    }
    delete values.name;
  }

  private shouldHideInternalFlag(): boolean {
    if (AWSProviderSettings) {
      if (AWSProviderSettings.loadBalancers && AWSProviderSettings.loadBalancers.inferInternalFlagFromSubnet) {
        delete this.props.formik.values.isInternal;
        return true;
      }
    }
    return false;
  }

  public componentDidMount(): void {
    if (this.props.isNew || this.props.forPipelineConfig) {
      this.loadAccounts();
      const hideInternalFlag = this.shouldHideInternalFlag();
      this.setState({ hideInternalFlag });
    }

    if (this.props.loadBalancer && this.props.isNew) {
      this.buildName();
    }
  }

  private loadAccounts(): void {
    AccountService.listAccounts('aws').then(accounts => {
      this.setState({ accounts });
      this.accountUpdated(this.props.formik.values.credentials);
    });
  }

  private getName(): string {
    const elb = this.props.formik.values;
    const elbName = [this.props.app.name, elb.stack || '', elb.detail || ''].join('-');
    return trimEnd(elbName, '-');
  }

  private internalFlagChanged = (event: React.ChangeEvent<any>): void => {
    this.setState({ internalFlagToggled: true });
    this.props.formik.handleChange(event);
  };

  private getAvailabilityZones(regions: IRegion[]): string[] {
    const { setFieldValue, values } = this.props.formik;
    const selected = regions ? regions.filter(region => region.name === values.region) : [];
    if (selected.length) {
      const newRegionZones = uniq(selected[0].availabilityZones);
      if (!isEqual(newRegionZones, values.regionZones)) {
        setFieldValue('regionZones', newRegionZones);
      }
      return newRegionZones;
    } else {
      return [];
    }
  }

  private getAvailableSubnets(): IPromise<ISubnet[]> {
    const { credentials, region } = this.props.formik.values;
    return SubnetReader.listSubnets().then(subnets => {
      return chain(subnets)
        .filter({ account: credentials, region })
        .reject({ target: 'ec2' })
        .reject({ purpose: null })
        .value();
    });
  }

  private setSubnetTypeFromVpc(subnetOptions: { [purpose: string]: ISubnetOption }): void {
    const { setFieldValue, values } = this.props.formik;
    if (values.vpcId) {
      const currentSelection = find(subnetOptions, option => option.vpcIds.includes(values.vpcId));
      if (currentSelection) {
        values.subnetType = currentSelection.purpose;
      }
      setFieldValue('vpcId', null);
    }
  }

  private subnetUpdated(subnets: ISubnetOption[]): void {
    const { setFieldValue, values } = this.props.formik;

    const subnetPurpose = values.subnetType || null,
      subnet = subnets.find(test => test.purpose === subnetPurpose),
      availableVpcIds = subnet ? subnet.vpcIds : [];

    let availabilityZones: string[];

    if (subnetPurpose) {
      setFieldValue('vpcId', availableVpcIds.length ? availableVpcIds[0] : null);
      if (!this.state.hideInternalFlag && !this.state.internalFlagToggled) {
        setFieldValue('isInternal', subnetPurpose.includes('internal'));
      }
      availabilityZones = uniq(subnets.find(o => o.purpose === values.subnetType).availabilityZones.sort());
    } else {
      availabilityZones = this.getAvailabilityZones(this.state.regions);
      setFieldValue('vpcId', null);
    }
    this.setState({ availabilityZones });
  }

  private handleSubnetUpdated = (): void => {
    this.subnetUpdated(this.state.subnets);
  };

  private updateSubnets(): void {
    this.getAvailableSubnets().then(availableSubnets => {
      const subnetOptions = availableSubnets.reduce(
        (accumulator, subnet) => {
          if (!accumulator[subnet.purpose]) {
            accumulator[subnet.purpose] = {
              purpose: subnet.purpose,
              label: subnet.label,
              deprecated: subnet.deprecated,
              vpcIds: [],
              availabilityZones: [],
            } as ISubnetOption;
          }
          const acc = accumulator[subnet.purpose];
          if (acc.vpcIds.indexOf(subnet.vpcId) === -1) {
            acc.vpcIds.push(subnet.vpcId);
          }
          acc.availabilityZones.push(subnet.availabilityZone);
          acc.availabilityZones = uniq(acc.availabilityZones);
          return accumulator;
        },
        {} as { [purpose: string]: ISubnetOption },
      );

      this.setSubnetTypeFromVpc(subnetOptions);

      if (!subnetOptions[this.props.formik.values.subnetType]) {
        this.props.formik.values.subnetType = '';
        this.props.formik.setFieldValue('subnetType', '');
      }
      const subnets = Object.keys(subnetOptions).map(k => subnetOptions[k]);
      this.setState({ subnets });
      this.subnetUpdated(subnets);
    });
  }

  protected updateExistingLoadBalancerNames(): void {
    const { credentials, region } = this.props.formik.values;

    const accountLoadBalancersByRegion: { [region: string]: string[] } = {};
    this.props.app
      .getDataSource('loadBalancers')
      .refresh(true)
      .then(() => {
        this.props.app.getDataSource('loadBalancers').data.forEach(loadBalancer => {
          if (loadBalancer.account === credentials) {
            accountLoadBalancersByRegion[loadBalancer.region] = accountLoadBalancersByRegion[loadBalancer.region] || [];
            accountLoadBalancersByRegion[loadBalancer.region].push(loadBalancer.name);
          }
        });

        this.setState({ existingLoadBalancerNames: accountLoadBalancersByRegion[region] || [] });
        this.props.revalidate();
      });
  }

  private updateName(): void {
    const loadBalancerCommand = this.props.formik.values;
    const moniker: IMoniker = {
      app: this.props.app.name,
      cluster: this.getName(),
      stack: loadBalancerCommand.stack,
      detail: loadBalancerCommand.detail,
    };
    loadBalancerCommand.moniker = moniker;
    this.props.formik.setFieldValue('name', this.getName());
  }

  private accountUpdated = (account: string): void => {
    this.props.formik.setFieldValue('credentials', account);
    AccountService.getRegionsForAccount(account).then(regions => {
      const availabilityZones = this.getAvailabilityZones(regions);
      this.setState({ availabilityZones, regions });
      this.updateExistingLoadBalancerNames();
      this.updateSubnets();
      this.updateName();
    });
  };

  private regionUpdated = (region: string): void => {
    this.props.formik.setFieldValue('region', region);
    const availabilityZones = this.getAvailabilityZones(this.state.regions.filter(r => r.name === region));
    this.setState({ availabilityZones });
    this.updateExistingLoadBalancerNames();
    this.updateSubnets();
    this.updateName();
  };

  private stackChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const stack = event.target.value;
    this.props.formik.values.stack = stack;
    this.props.formik.setFieldValue('stack', stack);
    this.updateName();
  };

  private detailChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const detail = event.target.value;
    this.props.formik.values.detail = detail;
    this.props.formik.setFieldValue('detail', detail);
    this.updateName();
  };

  private handleAvailabilityZonesChanged = (zones: string[]): void => {
    this.props.formik.setFieldValue('regionZones', zones);
  };

  public render() {
    const { app } = this.props;
    const { errors, values } = this.props.formik;
    const { accounts, availabilityZones, hideInternalFlag, regions, subnets } = this.state;

    const className = classNames({
      'col-md-12': true,
      well: true,
      'alert-danger': errors.name,
      'alert-info': !errors.name,
    });

    return (
      <div className="container-fluid form-horizontal">
        {!accounts && (
          <div style={{ height: '200px' }}>
            <Spinner size="medium" />
          </div>
        )}
        {accounts && (
          <div className="modal-body">
            <div className="form-group">
              <div className={className}>
                <strong>Your load balancer will be named: </strong>
                <span>{values.name}</span>
                <HelpField id="aws.loadBalancer.name" />
                <Field type="text" style={{ display: 'none' }} className="form-control input-sm no-spel" name="name" />
                {errors.name && <ValidationMessage type="error" message={errors.name} />}
              </div>
            </div>
            <div className="form-group">
              <div className="col-md-3 sm-label-right">Account</div>
              <div className="col-md-7">
                <AccountSelectInput
                  value={values.credentials}
                  onChange={evt => this.accountUpdated(evt.target.value)}
                  accounts={accounts}
                  provider="aws"
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
                Stack <HelpField id="aws.loadBalancer.stack" />
              </div>
              <div className="col-md-3">
                <input
                  type="text"
                  className={`form-control input-sm no-spel ${errors.stack ? 'invalid' : ''}`}
                  value={values.stack}
                  name="stack"
                  onChange={this.stackChanged}
                />
              </div>
              <div className="col-md-6 form-inline">
                <label className="sm-label-right">
                  <span>
                    Detail <HelpField id="aws.loadBalancer.detail" />{' '}
                  </span>
                </label>
                <input
                  type="text"
                  className={`form-control input-sm no-spel ${errors.detail ? 'invalid' : ''}`}
                  value={values.detail}
                  name="detail"
                  onChange={this.detailChanged}
                />
              </div>
              {errors.stack && (
                <div className="col-md-7 col-md-offset-3">
                  <ValidationMessage type="error" message={errors.stack} />
                </div>
              )}
              {errors.detail && (
                <div className="col-md-7 col-md-offset-3">
                  <ValidationMessage type="error" message={errors.detail} />
                </div>
              )}
            </div>

            <AvailabilityZoneSelector
              credentials={values.credentials}
              region={values.region}
              onChange={this.handleAvailabilityZonesChanged}
              selectedZones={values.regionZones}
              allZones={availabilityZones}
            />
            <SubnetSelectField
              labelColumns={3}
              helpKey="aws.loadBalancer.subnet"
              component={values}
              field="subnetType"
              region={values.region}
              subnets={subnets as any}
              application={app}
              onChange={this.handleSubnetUpdated}
            />
            {values.vpcId &&
              !hideInternalFlag && (
                <div className="form-group">
                  <div className="col-md-3 sm-label-right">
                    <b>Internal</b> <HelpField id="aws.loadBalancer.internal" />
                  </div>
                  <div className="col-md-7 checkbox">
                    <label>
                      <input type="checkbox" name="isInternal" onChange={this.internalFlagChanged} />
                      Create an internal load balancer
                    </label>
                  </div>
                </div>
              )}
          </div>
        )}
      </div>
    );
  }
}

export const LoadBalancerLocation = wizardPage(LoadBalancerLocationImpl);

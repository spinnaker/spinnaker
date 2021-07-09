import { FormikErrors, FormikProps } from 'formik';
import { forOwn, uniqBy } from 'lodash';
import React from 'react';
import { Option } from 'react-select';
import { combineLatest as observableCombineLatest, from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  Application,
  FormikFormField,
  HelpField,
  IAccount,
  IRegion,
  ISecurityGroupsByAccountSourceData,
  ISubnet,
  IVpc,
  IWizardPageComponent,
  ReactInjector,
  ReactSelectInput,
  SubnetReader,
  TetheredSelect,
} from '@spinnaker/core';
import { IAmazonFunctionUpsertCommand } from '../../index';
import { VpcReader } from '../../vpc';

export interface ISubnetOption {
  subnetId: string;
  vpcId: string;
}

export interface INetworkProps {
  formik: FormikProps<IAmazonFunctionUpsertCommand>;
  isNew?: boolean;
  app: Application;
}

export interface INetworkState {
  vpcOptions: Array<{}>;
  accounts: IAccount[];
  regions: IRegion[];
  subnets: ISubnetOption[];
  availableSubnets: ISubnetOption[];
  securityGroups: ISecurityGroupsByAccountSourceData;
}

export class Network
  extends React.Component<INetworkProps, INetworkState>
  implements IWizardPageComponent<IAmazonFunctionUpsertCommand> {
  constructor(props: INetworkProps) {
    super(props);
    this.getAllVpcs();
  }

  public state: INetworkState = {
    vpcOptions: [],
    accounts: null,
    regions: [],
    subnets: [],
    availableSubnets: [],
    securityGroups: null,
  };
  private props$ = new Subject<INetworkProps>();
  private destroy$ = new Subject<void>();

  private getAllVpcs(): void {
    observableFrom(VpcReader.listVpcs())
      .pipe(takeUntil(this.destroy$))
      .subscribe((Vpcs) => {
        this.setState({ vpcOptions: Vpcs });
      });
  }

  public validate(): FormikErrors<IAmazonFunctionUpsertCommand> {
    return {};
  }

  private getAvailableSubnets(): PromiseLike<ISubnet[]> {
    return SubnetReader.listSubnetsByProvider('aws');
  }

  private getAvailableSecurityGroups(): PromiseLike<ISecurityGroupsByAccountSourceData> {
    return ReactInjector.securityGroupReader.getAllSecurityGroups();
  }
  private makeSubnetOptions(availableSubnets: ISubnet[]): ISubnetOption[] {
    const subOptions: ISubnetOption[] = availableSubnets.map((s) => ({ subnetId: s.id, vpcId: s.vpcId }));
    // we have to filter out any duplicate options
    const uniqueSubOptions = uniqBy(subOptions, 'subnetId');
    return uniqueSubOptions;
  }

  public componentDidUpdate() {
    this.props$.next(this.props);
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  public componentDidMount(): void {
    const allSubnets = Promise.resolve(this.getAvailableSubnets())
      .then((subnets: ISubnet[]) => {
        subnets.forEach((subnet: ISubnet) => {
          subnet.label = subnet.id;
          subnet.deprecated = !!subnet.deprecated;
          if (subnet.deprecated) {
            subnet.label += ' (deprecated)';
          }
        });
        return subnets.filter((s) => s.label);
      })
      .then((subnets: ISubnet[]) => {
        return this.makeSubnetOptions(subnets);
      });

    const secGroups$ = Promise.resolve(this.getAvailableSecurityGroups());
    observableCombineLatest([allSubnets, secGroups$])
      .pipe(takeUntil(this.destroy$))
      .subscribe(([availableSubnets, securityGroups]) => {
        return this.setState({ availableSubnets, securityGroups });
      });
  }

  private handleSubnetUpdate = (options: Array<Option<string>>) => {
    const subnetsSelected = options.map((o) => o.value);
    this.props.formik.setFieldValue('subnetIds', subnetsSelected);
  };

  private handleSecurityGroupsUpdate = (options: Array<Option<string>>) => {
    const sgSelected = options.map((o) => o.value);
    this.props.formik.setFieldValue('securityGroupIds', sgSelected);
  };

  private setVpc = (vpcId: string): void => {
    this.props.formik.setFieldValue('vpcId', vpcId);
    this.props.formik.setFieldValue('subnetIds', []);
    const { availableSubnets } = this.state;
    const subs = availableSubnets.filter(function (s: ISubnetOption) {
      return s.vpcId.includes(vpcId);
    });
    this.setState({ subnets: subs });
  };

  private toSubnetOption = (value: ISubnetOption): Option<string> => {
    return { value: value.subnetId, label: value.subnetId };
  };

  private getSecurityGroupsByVpc = (sgs: ISecurityGroupsByAccountSourceData): Array<Option<string>> => {
    const { values } = this.props.formik;
    const sgOptions: Array<Option<string>> = [];
    /** Get security groups that belong to current selected account */
    forOwn(sgs, function (sgByAccount, acc) {
      if (acc === values.credentials) {
        /** Get security groups that fall under the provider 'aws' */
        forOwn(sgByAccount, function (sgByRegion, provider) {
          if (provider === 'aws') {
            /** Get security groups that are under the current selected region */
            forOwn(sgByRegion, function (groups, region) {
              if (region === values.region) {
                /** Get security groups that fall under the current selected VPC */
                groups.forEach(function (group) {
                  if (group.vpcId === values.vpcId) {
                    sgOptions.push({ value: group.id, label: group.name });
                  }
                });
              }
            });
          }
        });
      }
    });
    return sgOptions;
  };

  private getSubnetOptions = (): Array<Option<string>> => {
    const { subnets, availableSubnets } = this.state;
    const { values } = this.props.formik;
    if (!this.props.isNew && values.vpcId) {
      return availableSubnets
        .filter(function (s: ISubnetOption) {
          return s.vpcId.includes(values.vpcId);
        })
        .map(this.toSubnetOption);
    } else {
      return subnets.map(this.toSubnetOption);
    }
  };

  public render() {
    const { vpcOptions, securityGroups } = this.state;
    const { values } = this.props.formik;
    const subnetOptions = this.getSubnetOptions();
    const sgOptions = securityGroups ? this.getSecurityGroupsByVpc(securityGroups) : [];
    return (
      <div className="form-group">
        <div className="col-md-11">
          <div className="sp-margin-m-bottom">
            {values.credentials && (
              <FormikFormField
                name="vpcId"
                label="VPC Id"
                help={<HelpField id="aws.function.vpc.id" />}
                input={(props) => (
                  <ReactSelectInput
                    {...props}
                    stringOptions={vpcOptions
                      .filter((v: IVpc) => v.account === values.credentials)
                      .map((v: IVpc) => v.id)}
                    clearable={true}
                  />
                )}
                onChange={this.setVpc}
                required={false}
              />
            )}
          </div>
          <div className="form-group">
            <div className="col-md-4 sm-label-right">
              <b>Subnets </b>
              <HelpField id="aws.function.subnet" />
            </div>
            <div className="col-md-7">
              {subnetOptions.length === 0 && (
                <div className="form-control-static">No subnets found in the selected account/region/VPC</div>
              )}
              {values.vpcId ? (
                <TetheredSelect
                  multi={true}
                  options={subnetOptions}
                  value={values.subnetIds}
                  onChange={this.handleSubnetUpdate}
                />
              ) : null}
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-4 sm-label-right">
              <b>Security Groups </b>
              <HelpField id="aws.function.subnet" />
            </div>
            <div className="col-md-7">
              {sgOptions.length === 0 && (
                <div className="form-control-static">No security groups found in the selected account/region/VPC</div>
              )}
              {values.credentials && values.vpcId ? (
                <TetheredSelect
                  multi={true}
                  options={sgOptions}
                  value={values.securityGroupIds}
                  onChange={this.handleSecurityGroupsUpdate}
                />
              ) : null}
            </div>
          </div>
        </div>
      </div>
    );
  }
}

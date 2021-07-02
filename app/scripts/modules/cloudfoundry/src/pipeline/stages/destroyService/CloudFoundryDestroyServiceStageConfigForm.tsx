import React from 'react';
import Select, { Option } from 'react-select';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountService,
  Application,
  IAccount,
  IFormikStageConfigInjectedProps,
  IRegion,
  StageConfigField,
} from '@spinnaker/core';

export interface ICloudfoundryDestroyServiceStageConfigState {
  accounts: IAccount[];
  regions: string[];
  application: Application;
}

export class CloudFoundryDestroyServiceStageConfigForm extends React.Component<
  IFormikStageConfigInjectedProps,
  ICloudfoundryDestroyServiceStageConfigState
> {
  private destroy$ = new Subject();

  constructor(props: IFormikStageConfigInjectedProps, context: any) {
    super(props, context);
    this.props.formik.setFieldValue('cloudProvider', 'cloudfoundry');
    this.state = {
      accounts: [],
      regions: [],
      application: props.application,
    };
  }

  public componentDidMount() {
    const stage = this.props.formik.values;
    this.props.formik.setFieldValue('application', this.state.application.name);
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((rawAccounts: IAccount[]) => this.setState({ accounts: rawAccounts }));
    if (stage.credentials) {
      this.loadRegions(stage.credentials);
    }
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private loadRegions = (creds: string) => {
    this.setState({ regions: [] });
    observableFrom(AccountService.getRegionsForAccount(creds))
      .pipe(takeUntil(this.destroy$))
      .subscribe((regionList: IRegion[]) => {
        const regions = regionList.map((r) => r.name);
        regions.sort((a, b) => a.localeCompare(b));
        this.setState({ regions: regions });
      });
  };

  private accountUpdated = (option: Option<string>) => {
    const creds = option.value;
    this.props.formik.setFieldValue('credentials', creds);
    this.props.formik.setFieldValue('region', '');
    if (creds) {
      this.loadRegions(creds);
    }
  };

  private regionUpdated = (option: Option<string>) => {
    this.props.formik.setFieldValue('region', option.value);
  };

  private serviceInstanceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.formik.setFieldValue('serviceInstanceName', event.target.value);
  };

  private removeBindingsUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.formik.setFieldValue('removeBindings', event.target.checked);
  };

  public render() {
    const stage = this.props.formik.values;
    const { accounts, regions } = this.state;
    const { credentials, region, serviceInstanceName } = stage;
    return (
      <div className="form-horizontal">
        <StageConfigField label="Account">
          <Select
            options={
              accounts &&
              accounts.map((acc: IAccount) => ({
                label: acc.name,
                value: acc.name,
              }))
            }
            clearable={false}
            value={credentials}
            onChange={this.accountUpdated}
          />
        </StageConfigField>
        <StageConfigField label="Region">
          <Select
            options={
              regions &&
              regions.map((r: string) => ({
                label: r,
                value: r,
              }))
            }
            clearable={false}
            value={region}
            onChange={this.regionUpdated}
          />
        </StageConfigField>
        <StageConfigField label="Service Instance Name">
          <input
            type="text"
            className="form-control"
            required={true}
            onChange={this.serviceInstanceNameUpdated}
            value={serviceInstanceName}
          />
        </StageConfigField>
        <StageConfigField label="Remove Bindings">
          <input type="checkbox" checked={stage.removeBindings} onChange={this.removeBindingsUpdated} />
          {stage.removeBindings && (
            <div>
              Warning: Prior to destroying, this will attempt to unbind any server groups that are bound to this
              service. This stage will fail if any server groups don't belong to the spinnaker application.
            </div>
          )}
        </StageConfigField>
      </div>
    );
  }
}

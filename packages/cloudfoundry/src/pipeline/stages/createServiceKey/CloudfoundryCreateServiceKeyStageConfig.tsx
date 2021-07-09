import React from 'react';
import { Option } from 'react-select';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountService,
  IAccount,
  IRegion,
  IStageConfigProps,
  ReactSelectInput,
  StageConfigField,
  TextInput,
} from '@spinnaker/core';

interface ICloudfoundryCreateServiceKeyStageConfigState {
  accounts: string[];
  regions: string[];
}

export class CloudfoundryCreateServiceKeyStageConfig extends React.Component<
  IStageConfigProps,
  ICloudfoundryCreateServiceKeyStageConfigState
> {
  private destroy$ = new Subject();

  constructor(props: IStageConfigProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    this.state = {
      accounts: [],
      regions: [],
    };
  }

  public componentDidMount(): void {
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((rawAccounts: IAccount[]) => this.setState({ accounts: rawAccounts.map((it) => it.name) }));
    if (this.props.stage.credentials) {
      this.clearAndReloadRegions();
    }
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private clearAndReloadRegions = () => {
    this.setState({ regions: [] });
    observableFrom(AccountService.getRegionsForAccount(this.props.stage.credentials))
      .pipe(takeUntil(this.destroy$))
      .subscribe((regionList: IRegion[]) => {
        const regions = regionList.map((r) => r.name);
        regions.sort((a, b) => a.localeCompare(b));
        this.setState({ regions });
      });
  };

  private serviceInstanceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.updateStageField({ serviceInstanceName: event.target.value });
  };

  private serviceKeyNameUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.updateStageField({ serviceKeyName: event.target.value });
  };

  private accountUpdated = (option: Option<string>) => {
    const credentials = option.target.value;
    this.setState({
      regions: [],
    });
    this.props.updateStageField({
      credentials,
      region: '',
    });
    this.clearAndReloadRegions();
  };

  private regionUpdated = (option: Option<string>) => {
    const region = option.target.value;
    this.props.updateStageField({
      region,
    });
  };

  public render() {
    const { credentials, region, serviceInstanceName, serviceKeyName } = this.props.stage;
    const { accounts, regions } = this.state;

    return (
      <div className="form-horizontal">
        <StageConfigField label="Account">
          <ReactSelectInput
            clearable={false}
            onChange={this.accountUpdated}
            value={credentials}
            stringOptions={accounts}
          />
        </StageConfigField>
        <StageConfigField label="Region">
          <ReactSelectInput clearable={false} onChange={this.regionUpdated} value={region} stringOptions={regions} />
        </StageConfigField>
        <StageConfigField label="Service Instance Name">
          <TextInput
            type="text"
            className="form-control"
            onChange={this.serviceInstanceNameUpdated}
            value={serviceInstanceName}
          />
        </StageConfigField>
        <StageConfigField label="Service Key Name">
          <TextInput
            type="text"
            className="form-control"
            onChange={this.serviceKeyNameUpdated}
            value={serviceKeyName}
          />
        </StageConfigField>
      </div>
    );
  }
}

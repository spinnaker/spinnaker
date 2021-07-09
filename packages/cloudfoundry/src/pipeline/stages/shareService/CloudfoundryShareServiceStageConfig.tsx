import React from 'react';
import Select, { Option } from 'react-select';
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

interface ICloudfoundryShareServiceStageConfigState {
  accounts: IAccount[];
  regions: string[];
  shareToRegionsList: string[];
}

export class CloudfoundryShareServiceStageConfig extends React.Component<
  IStageConfigProps,
  ICloudfoundryShareServiceStageConfigState
> {
  private destroy$ = new Subject();

  constructor(props: IStageConfigProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    this.state = {
      accounts: [],
      regions: [],
      shareToRegionsList: [],
    };
  }

  public componentDidMount(): void {
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((accounts) => this.setState({ accounts }));
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
        const { region } = this.props.stage;
        const regions = regionList.map((r) => r.name);
        regions.sort((a, b) => a.localeCompare(b));
        this.setState({ regions });
        if (region) {
          this.clearAndResetShareToRegionList(region, regions);
        }
      });
  };

  private serviceInstanceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.updateStageField({ serviceInstanceName: event.target.value });
  };

  private clearAndResetShareToRegionList = (region: string, regions: string[]) => {
    this.setState({ shareToRegionsList: regions.filter((r) => r !== region) });
  };

  private accountUpdated = (option: Option<string>) => {
    const credentials = option.target.value;
    this.setState({
      regions: [],
      shareToRegionsList: [],
    });
    this.props.updateStageField({
      credentials,
      region: '',
      shareToRegions: [],
    });
    if (credentials) {
      this.clearAndReloadRegions();
    }
  };

  private regionUpdated = (option: Option<string>) => {
    const region = option.target.value;
    this.setState({ shareToRegionsList: [] });
    this.props.updateStageField({
      region,
      shareToRegions: [],
    });
    this.clearAndResetShareToRegionList(region, this.state.regions);
  };

  private shareToRegionsUpdated = (option: Option<string>) => {
    this.props.updateStageField({ shareToRegions: option.map((o: Option) => o.value) });
  };

  public render() {
    const { credentials, region, serviceInstanceName, shareToRegions } = this.props.stage;
    const { accounts, regions, shareToRegionsList } = this.state;

    return (
      <div className="form-horizontal">
        <StageConfigField label="Account">
          <ReactSelectInput
            clearable={false}
            onChange={this.accountUpdated}
            value={credentials}
            stringOptions={accounts.map((it) => it.name)}
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
        <StageConfigField label="Share To Regions">
          <Select
            options={
              shareToRegionsList &&
              shareToRegionsList.map((r: string) => ({
                label: r,
                value: r,
              }))
            }
            multi={true}
            clearable={false}
            value={shareToRegions}
            onChange={this.shareToRegionsUpdated}
          />
        </StageConfigField>
      </div>
    );
  }
}

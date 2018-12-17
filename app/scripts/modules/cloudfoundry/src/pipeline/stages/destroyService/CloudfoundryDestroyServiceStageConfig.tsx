import * as React from 'react';

import Select, { Option } from 'react-select';

import { AccountService, IAccount, IRegion, IStageConfigProps, StageConfigField } from '@spinnaker/core';

export interface ICloudfoundryDestroyServiceStageConfigState {
  accounts: IAccount[];
  cloudProvider: string;
  credentials: string;
  region: string;
  regions: IRegion[];
  serviceName: string;
}

export class CloudfoundryDestroyServiceStageConfig extends React.Component<
  IStageConfigProps,
  ICloudfoundryDestroyServiceStageConfigState
> {
  constructor(props: IStageConfigProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    this.state = {
      accounts: [],
      cloudProvider: 'cloudfoundry',
      credentials: props.stage.credentials,
      region: props.stage.region,
      regions: [],
      serviceName: props.stage.serviceName,
    };
  }

  public componentDidMount = (): void => {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts: accounts });
      const { credentials } = this.props.stage;
      if (credentials) {
        this.clearAndReloadRegions();
      }
    });
    this.props.stageFieldUpdated();
  };

  private clearAndReloadRegions = (): void => {
    this.setState({ regions: [] });
    AccountService.getRegionsForAccount(this.props.stage.credentials).then(regions => this.setState({ regions }));
  };

  private accountUpdated = (option: Option<string>): void => {
    const credentials = option.value;
    this.props.stage.credentials = credentials;
    this.props.stage.region = '';
    this.props.stageFieldUpdated();
    if (credentials) {
      this.clearAndReloadRegions();
    }
  };

  private regionUpdated = (option: Option<string>): void => {
    const region = option.value;
    this.setState({ region });
    this.props.stage.region = region;
    this.props.stageFieldUpdated();
  };

  private serviceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const serviceName = event.target.value;
    this.setState({ serviceName });
    this.props.stage.serviceName = serviceName;
    this.props.stageFieldUpdated();
  };

  private timeoutUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.stage.timeout = event.target.value;
    this.props.stageFieldUpdated();
  };

  public render() {
    const { stage } = this.props;
    const { credentials, region, serviceName, timeout } = stage;
    const { accounts, regions } = this.state;
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
              regions.map((r: IRegion) => ({
                label: r.name,
                value: r.name,
              }))
            }
            clearable={false}
            value={region}
            onChange={this.regionUpdated}
          />
        </StageConfigField>
        <StageConfigField label="Service Name">
          <input
            type="text"
            className="form-control"
            required={true}
            onChange={this.serviceNameUpdated}
            value={serviceName}
          />
        </StageConfigField>
        <StageConfigField label="Override Destroy Timeout (Seconds)" helpKey="cf.service.destroy.timeout">
          <input type="number" className="form-control" onChange={this.timeoutUpdated} value={timeout} />
        </StageConfigField>
      </div>
    );
  }
}

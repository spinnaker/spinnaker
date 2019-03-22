import * as React from 'react';

import Select, { Option } from 'react-select';

import { AccountService, IAccount, IRegion, IStageConfigProps, StageConfigField } from '@spinnaker/core';

export interface ICloudfoundryDestroyServiceStageConfigState {
  accounts: IAccount[];
  regions: IRegion[];
}

export class CloudfoundryDestroyServiceStageConfig extends React.Component<
  IStageConfigProps,
  ICloudfoundryDestroyServiceStageConfigState
> {
  constructor(props: IStageConfigProps) {
    super(props);
    this.props.updateStageField({ cloudProvider: 'cloudfoundry' });
    this.state = {
      accounts: [],
      regions: [],
    };
  }

  public componentDidMount = () => {
    AccountService.listAccounts('cloudfoundry').then((accounts: IAccount[]) => {
      this.setState({ accounts });
      if (this.props.stage.credentials) {
        this.clearAndReloadRegions();
      }
    });
  };

  private clearAndReloadRegions = () => {
    this.setState({ regions: [] });
    AccountService.getRegionsForAccount(this.props.stage.credentials).then((regions: IRegion[]) =>
      this.setState({ regions }),
    );
  };

  private accountUpdated = (option: Option<string>) => {
    const credentials = option.value;
    this.props.updateStageField({
      credentials,
      region: '',
    });
    if (credentials) {
      this.clearAndReloadRegions();
    }
  };

  private regionUpdated = (option: Option<string>) => {
    this.props.updateStageField({ region: option.value });
  };

  private serviceInstanceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.updateStageField({ serviceInstanceName: event.target.value });
  };

  public render() {
    const { stage } = this.props;
    const { credentials, region, serviceInstanceName } = stage;
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
        <StageConfigField label="Service Instance Name">
          <input
            type="text"
            className="form-control"
            required={true}
            onChange={this.serviceInstanceNameUpdated}
            value={serviceInstanceName}
          />
        </StageConfigField>
      </div>
    );
  }
}

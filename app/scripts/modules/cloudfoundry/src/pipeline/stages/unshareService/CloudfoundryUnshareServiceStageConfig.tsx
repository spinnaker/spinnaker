import * as React from 'react';

import Select, { Option } from 'react-select';

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
}

export class CloudfoundryUnshareServiceStageConfig extends React.Component<
  IStageConfigProps,
  ICloudfoundryShareServiceStageConfigState
> {
  constructor(props: IStageConfigProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    this.state = {
      accounts: [],
      regions: [],
    };
  }

  public componentDidMount = () => {
    AccountService.listAccounts('cloudfoundry').then((accounts: IAccount[]) => {
      this.setState({ accounts });
    });
    if (this.props.stage.credentials) {
      this.clearAndReloadRegions();
    }
  };

  private clearAndReloadRegions = () => {
    this.setState({ regions: [] });
    AccountService.getRegionsForAccount(this.props.stage.credentials).then((regionList: IRegion[]) => {
      const regions = regionList.map(r => r.name);
      regions.sort((a, b) => a.localeCompare(b));
      this.setState({ regions });
    });
  };

  private serviceInstanceNameUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.updateStageField({ serviceInstanceName: event.target.value });
  };

  private accountUpdated = (option: Option<string>) => {
    const credentials = option.target.value;
    this.setState({ regions: [] });
    this.props.updateStageField({
      credentials,
      unshareFromRegions: [],
    });
    if (credentials) {
      this.clearAndReloadRegions();
    }
  };

  private unshareFromRegionsUpdated = (option: Option<string>) => {
    this.props.updateStageField({ unshareFromRegions: option.map((o: Option) => o.value) });
  };

  public render() {
    const { credentials, serviceInstanceName, unshareFromRegions } = this.props.stage;
    const { accounts, regions } = this.state;

    return (
      <div className="form-horizontal">
        <StageConfigField label="Account">
          <ReactSelectInput
            clearable={false}
            onChange={this.accountUpdated}
            value={credentials}
            stringOptions={accounts.map(it => it.name)}
          />
        </StageConfigField>
        <StageConfigField label="Service Instance Name">
          <TextInput
            type="text"
            className="form-control"
            onChange={this.serviceInstanceNameUpdated}
            value={serviceInstanceName}
          />
        </StageConfigField>
        <StageConfigField label="Unshare From Regions">
          <Select
            options={
              regions &&
              regions.map((r: string) => ({
                label: r,
                value: r,
              }))
            }
            multi={true}
            clearable={false}
            value={unshareFromRegions}
            onChange={this.unshareFromRegionsUpdated}
          />
        </StageConfigField>
      </div>
    );
  }
}

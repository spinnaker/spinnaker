import * as React from 'react';
import {
  AccountSelectInput,
  AccountService,
  IAccount,
  IRegion,
  IStageConfigProps,
  RegionSelectField,
  StageConfigField,
} from '@spinnaker/core';

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
    AccountService.getRegionsForAccount(this.props.stage.credentials).then(regions =>
      this.setState({ regions: regions }),
    );
  };

  private accountUpdated = (credentials: string): void => {
    this.props.stage.credentials = credentials;
    this.props.stage.region = '';
    this.props.stageFieldUpdated();
    if (credentials) {
      this.clearAndReloadRegions();
    }
  };

  private regionUpdated = (region: string): void => {
    this.setState({ region: region });
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
    const { credentials, serviceName, timeout } = stage;
    const { accounts, regions } = this.state;
    return (
      <div className="form-horizontal">
        <StageConfigField label="Account">
          <AccountSelectInput
            value={stage.credentials}
            onChange={evt => this.accountUpdated(evt.target.value)}
            accounts={accounts}
            provider="cloudfoundry"
          />
        </StageConfigField>
        <RegionSelectField
          labelColumns={3}
          fieldColumns={8}
          component={stage}
          field="region"
          account={credentials}
          onChange={this.regionUpdated}
          regions={regions}
        />
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

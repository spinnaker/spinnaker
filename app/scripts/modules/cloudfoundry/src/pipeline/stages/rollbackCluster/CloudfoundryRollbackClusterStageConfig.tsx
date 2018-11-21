import * as React from 'react';
import {
  AccountService,
  Application,
  IAccount,
  IPipeline,
  IRegion,
  IStageConfigProps,
  NgReact,
  StageConfigField,
} from '@spinnaker/core';

export interface ICloudfoundryRollbackClusterStageProps extends IStageConfigProps {
  pipeline: IPipeline;
}

export interface ICloudfoundryRollbackClusterStageConfigState {
  accounts: IAccount[];
  application: Application;
  cloudProvider: string;
  credentials: string;
  pipeline: IPipeline;
  regions: IRegion[];
  targetHealthyRollbackPercentage: number;
  waitTimeBetweenRegions: number;
}

export class CloudfoundryRollbackClusterStageConfig extends React.Component<
  ICloudfoundryRollbackClusterStageProps,
  ICloudfoundryRollbackClusterStageConfigState
> {
  constructor(props: ICloudfoundryRollbackClusterStageProps) {
    super(props);

    Object.assign(props.stage, {
      targetHealthyRollbackPercentage: 100,
    });

    this.state = {
      accounts: [],
      application: props.application,
      cloudProvider: 'cloudfoundry',
      credentials: props.stage.credentials,
      pipeline: props.pipeline,
      regions: [],
      targetHealthyRollbackPercentage: props.stage.targetHealthyRollbackPercentage,
      waitTimeBetweenRegions: props.stage.waitTimeBetweenRegions,
    };
  }

  public componentDidMount = (): void => {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts: accounts });
    });
    this.props.stageFieldUpdated();
  };

  private waitTimeBetweenRegionsUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const time = parseInt(event.target.value || '0', 10);
    this.setState({ waitTimeBetweenRegions: time });
    this.props.stage.waitTimeBetweenRegions = time;
    this.props.stageFieldUpdated();
  };

  public render() {
    const { stage, stageFieldUpdated } = this.props;
    const { waitTimeBetweenRegions } = stage;
    const { accounts, application, pipeline } = this.state;
    const { AccountRegionClusterSelector } = NgReact;
    return (
      <div className="form-horizontal">
        {!pipeline.strategy && (
          <div>
            <AccountRegionClusterSelector
              application={application}
              clusterField="cluster"
              component={stage}
              onAccountUpdate={stageFieldUpdated}
              accounts={accounts}
            />
          </div>
        )}

        {stage.regions.length > 1 && (
          <StageConfigField label="Wait">
            <input
              type="number"
              min="0"
              value={waitTimeBetweenRegions}
              onChange={this.waitTimeBetweenRegionsUpdated}
              className="form-control input-sm inline-number"
            />
            &nbsp;seconds between regional rollbacks.
          </StageConfigField>
        )}
      </div>
    );
  }
}

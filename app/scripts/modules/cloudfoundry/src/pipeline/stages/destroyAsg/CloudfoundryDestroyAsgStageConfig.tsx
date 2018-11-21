import * as React from 'react';
import {
  AccountService,
  Application,
  IAccount,
  IPipeline,
  IRegion,
  IStageConfigProps,
  NgReact,
  StageConstants,
  StageConfigField,
} from '@spinnaker/core';

export interface ICloudfoundryDestroyAsgStageProps extends IStageConfigProps {
  pipeline: IPipeline;
}

export interface ICloudfoundryDestroyAsgStageConfigState {
  accounts: IAccount[];
  application: Application;
  cloudProvider: string;
  credentials: string;
  pipeline: IPipeline;
  region: string;
  regions: IRegion[];
  serviceName: string;
  target: string;
}

export class CloudfoundryDestroyAsgStageConfig extends React.Component<
  ICloudfoundryDestroyAsgStageProps,
  ICloudfoundryDestroyAsgStageConfigState
> {
  constructor(props: ICloudfoundryDestroyAsgStageProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    this.state = {
      accounts: [],
      application: props.application,
      cloudProvider: 'cloudfoundry',
      credentials: props.stage.credentials,
      pipeline: props.pipeline,
      region: props.stage.region,
      regions: [],
      serviceName: props.stage.serviceName,
      target: props.stage.target,
    };
  }

  public componentDidMount = (): void => {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts: accounts });
      this.accountUpdated();
    });
    this.props.stageFieldUpdated();
  };

  private accountUpdated = (): void => {
    const { credentials } = this.props.stage;
    if (credentials) {
      AccountService.getRegionsForAccount(credentials).then(regions => {
        this.setState({ regions: regions });
      });
    }
  };

  private targetUpdated = (target: string) => {
    this.setState({ target: target });
    this.props.stage.target = target;
    this.props.stageFieldUpdated();
  };

  public render() {
    const { stage, stageFieldUpdated } = this.props;
    const { accounts, application, pipeline, target } = this.state;
    const { AccountRegionClusterSelector, TargetSelect } = NgReact;
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

        <StageConfigField label="Target">
          <TargetSelect model={{ target: target }} options={StageConstants.TARGET_LIST} onChange={this.targetUpdated} />
        </StageConfigField>
      </div>
    );
  }
}

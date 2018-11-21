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

export interface ICloudfoundryResizeAsgStageProps extends IStageConfigProps {
  pipeline: IPipeline;
}

export interface ICloudfoundryResizeAsgStageConfigState {
  accounts: IAccount[];
  action?: string;
  application: Application;
  capacity?: any;
  cloudProvider?: string;
  cloudProviderType?: string;
  credentials: string;
  diskInMb?: number;
  instanceCount?: number;
  interestingHealthProviderNames?: string[];
  memoryInMb?: number;
  pipeline: IPipeline;
  region?: string;
  regions: IRegion[];
  resizeLabel: string;
  resizeMessage: string;
  resizeType: any;
  target?: string;
}

export class CloudfoundryResizeAsgStageConfig extends React.Component<
  ICloudfoundryResizeAsgStageProps,
  ICloudfoundryResizeAsgStageConfigState
> {
  constructor(props: ICloudfoundryResizeAsgStageProps) {
    super(props);
    let interestingHealthProviderNames;
    if (
      props.stage.isNew &&
      props.application.attributes.platformHealthOnlyShowOverride &&
      props.application.attributes.platformHealthOnly
    ) {
      interestingHealthProviderNames = ['Cloud Foundry'];
    }
    const initStage = {
      action: 'scale_exact',
      capacity: props.stage.capacity || {},
      cloudProvider: 'cloudfoundry',
      cloudProviderType: props.stage.cloudProvider,
      diskInMb: props.stage.diskInMb || 1024,
      instanceCount: props.stage.instanceCount || 1,
      interestingHealthProviderNames: interestingHealthProviderNames,
      memoryInMb: props.stage.memoryInMb || 1024,
      target: props.stage.target,
    };
    Object.assign(props.stage, initStage);

    this.state = {
      accounts: [],
      application: props.application,
      credentials: props.stage.credentials,
      pipeline: props.pipeline,
      region: props.stage.region || '',
      regions: [],
      resizeLabel: 'Match capacity',
      resizeMessage: 'Scaled capacity will match the numbers entered',
      resizeType: 'exact',
    };
    Object.assign(this.state, initStage);
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

  private instanceCountUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const instanceCount = parseInt(event.target.value, 10);
    this.setState({ instanceCount: instanceCount });
    this.props.stage.instanceCount = instanceCount;
    this.props.stageFieldUpdated();
  };

  private memoryInMbUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const memoryInMb = parseInt(event.target.value, 10);
    this.setState({ memoryInMb: memoryInMb });
    this.props.stage.memoryInMb = memoryInMb;
    this.props.stageFieldUpdated();
  };

  private diskInMbUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const diskInMb = parseInt(event.target.value, 10);
    this.setState({ diskInMb: diskInMb });
    this.props.stage.diskInMb = diskInMb;
    this.props.stageFieldUpdated();
  };

  private targetUpdated = (target: string) => {
    this.setState({ target: target });
    this.props.stage.target = target;
    this.props.stageFieldUpdated();
  };

  public render() {
    const { accounts, application, pipeline, resizeLabel, resizeMessage } = this.state;
    const { stage } = this.props;
    const { diskInMb, instanceCount, memoryInMb, target } = this.props.stage;
    const { AccountRegionClusterSelector, TargetSelect } = NgReact;
    return (
      <div className="cloudfoundry-resize-asg-stage form-horizontal">
        {!pipeline.strategy && (
          <div>
            <AccountRegionClusterSelector
              application={application}
              clusterField="cluster"
              component={stage}
              onAccountUpdate={this.props.stageFieldUpdated}
              accounts={accounts}
            />
          </div>
        )}
        <StageConfigField label="Target">
          <TargetSelect model={{ target: target }} options={StageConstants.TARGET_LIST} onChange={this.targetUpdated} />
        </StageConfigField>
        <div className="form-group">
          <span className="col-md-3 sm-label-right" />
          <div className="col-md-9">
            <div className="col-md-3 sm-label-left">Instances</div>
            <div className="col-md-3 sm-label-left">Mem Mb</div>
            <div className="col-md-3 sm-label-left">Disk Mb</div>
          </div>
          <StageConfigField label={resizeLabel}>
            <div className="col-md-3">
              <input
                type="number"
                key="instanceCount"
                onChange={this.instanceCountUpdated}
                value={instanceCount}
                className="form-control input-sm"
              />
            </div>
            <div className="col-md-3">
              <input
                type="number"
                key="memoryInMb"
                onChange={this.memoryInMbUpdated}
                value={memoryInMb}
                className="form-control input-sm"
              />
            </div>
            <div className="col-md-3">
              <input
                type="number"
                key="diskInMb"
                onChange={this.diskInMbUpdated}
                value={diskInMb}
                className="form-control input-sm"
              />
            </div>
            <div className="col-md-9 col-md-offset-3">
              <em className="subinput-note">{resizeMessage}</em>
            </div>
          </StageConfigField>
        </div>
      </div>
    );
  }
}

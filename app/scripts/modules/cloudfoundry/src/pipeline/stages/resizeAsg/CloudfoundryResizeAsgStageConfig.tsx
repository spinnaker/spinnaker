import * as React from 'react';
import {
  AccountService,
  Application,
  IAccount,
  ICapacity,
  IPipeline,
  IRegion,
  IStageConfigProps,
  NgReact,
  StageConstants,
  StageConfigField,
} from '@spinnaker/core';

import { AccountRegionClusterSelector } from 'cloudfoundry/presentation';

export interface ICloudfoundryResizeAsgStageProps extends IStageConfigProps {
  pipeline: IPipeline;
}

export interface ICloudfoundryResizeAsgStageConfigState {
  accounts: IAccount[];
  action?: string;
  application: Application;
  capacity?: Partial<ICapacity>;
  cloudProvider?: string;
  credentials: string;
  diskQuota?: number;
  instanceCount?: number;
  interestingHealthProviderNames?: string[];
  memory?: number;
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
    props.stage.capacity = props.stage.capacity || {};
    props.stage.capacity.desired = props.stage.capacity.desired || 1;
    const initStage = {
      action: 'scale_exact',
      capacity: props.stage.capacity,
      cloudProvider: 'cloudfoundry',
      diskQuota: props.stage.diskQuota || 1024,
      instanceCount: props.stage.instanceCount || 1,
      interestingHealthProviderNames: interestingHealthProviderNames,
      memory: props.stage.memory || 1024,
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
    this.props.stage.capacity.desired = instanceCount;
    this.props.stage.capacity.min = instanceCount;
    this.props.stage.capacity.max = instanceCount;
    this.props.stageFieldUpdated();
  };

  private memoryUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const memory = parseInt(event.target.value, 10);
    this.setState({ memory: memory });
    this.props.stage.memory = memory;
    this.props.stageFieldUpdated();
  };

  private diskQuotaUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const diskQuota = parseInt(event.target.value, 10);
    this.setState({ diskQuota: diskQuota });
    this.props.stage.diskQuota = diskQuota;
    this.props.stageFieldUpdated();
  };

  private targetUpdated = (target: string) => {
    this.setState({ target: target });
    this.props.stage.target = target;
    this.props.stageFieldUpdated();
  };

  private componentUpdate = (stage: any): void => {
    this.props.stage.credentials = stage.credentials;
    this.props.stage.regions = stage.regions;
    this.props.stage.cluster = stage.cluster;
    this.props.stageFieldUpdated();
  };

  public render() {
    const { accounts, application, pipeline, resizeLabel, resizeMessage } = this.state;
    const { stage } = this.props;
    const { capacity, target } = this.props.stage;
    const diskQuota = this.props.stage.diskQuota;
    const instanceCount = capacity.desired;
    const memory = this.props.stage.memory;
    const { TargetSelect } = NgReact;
    return (
      <div className="cloudfoundry-resize-asg-stage form-horizontal">
        {!pipeline.strategy && (
          <AccountRegionClusterSelector
            accounts={accounts}
            application={application}
            cloudProvider={'cloudfoundry'}
            onComponentUpdate={this.componentUpdate}
            component={stage}
          />
        )}
        <StageConfigField label="Target">
          <TargetSelect model={{ target: target }} options={StageConstants.TARGET_LIST} onChange={this.targetUpdated} />
        </StageConfigField>
        <div className="form-group">
          <span className="col-md-3 sm-label-right" />
          <div className="col-md-9">
            <div className="col-md-3 sm-label-left">Instances</div>
            <div className="col-md-3 sm-label-left">Mem (MB)</div>
            <div className="col-md-3 sm-label-left">Disk (MB)</div>
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
                key="memory"
                onChange={this.memoryUpdated}
                value={memory}
                className="form-control input-sm"
              />
            </div>
            <div className="col-md-3">
              <input
                type="number"
                key="diskQuota"
                onChange={this.diskQuotaUpdated}
                value={diskQuota}
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

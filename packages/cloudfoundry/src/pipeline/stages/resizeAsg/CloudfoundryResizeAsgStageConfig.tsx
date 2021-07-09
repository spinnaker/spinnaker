import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountService,
  IAccount,
  IStageConfigProps,
  NgReact,
  StageConfigField,
  StageConstants,
} from '@spinnaker/core';
import { AccountRegionClusterSelector } from '../../../presentation';

export interface ICloudfoundryResizeAsgStageConfigState {
  accounts: IAccount[];
}

export class CloudfoundryResizeAsgStageConfig extends React.Component<
  IStageConfigProps,
  ICloudfoundryResizeAsgStageConfigState
> {
  private destroy$ = new Subject();

  constructor(props: IStageConfigProps) {
    super(props);
    const { stage } = props;
    let interestingHealthProviderNames;
    if (
      stage.isNew &&
      props.application.attributes.platformHealthOnlyShowOverride &&
      props.application.attributes.platformHealthOnly
    ) {
      interestingHealthProviderNames = ['Cloud Foundry'];
    }
    const { capacity } = stage;
    this.props.updateStageField({
      action: 'scale_exact',
      capacity: capacity && capacity.desired ? capacity : { desired: 1, min: 1, max: 1 },
      cloudProvider: 'cloudfoundry',
      diskQuota: stage.diskQuota || 1024,
      interestingHealthProviderNames: interestingHealthProviderNames,
      memory: stage.memory || 1024,
    });

    this.state = { accounts: [] };
  }

  public componentDidMount(): void {
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((accounts) => this.setState({ accounts }));
    this.props.stageFieldUpdated();
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private instanceCountUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const instanceCount = parseInt(event.target.value, 10);
    this.props.updateStageField({
      capacity: {
        desired: instanceCount,
        min: instanceCount,
        max: instanceCount,
      },
    });
  };

  private memoryUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.updateStageField({ memory: parseInt(event.target.value, 10) });
  };

  private diskQuotaUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.updateStageField({ diskQuota: parseInt(event.target.value, 10) });
  };

  private targetUpdated = (target: string) => {
    this.props.updateStageField({ target });
  };

  private componentUpdated = (stage: any): void => {
    this.props.updateStageField({
      credentials: stage.credentials,
      regions: stage.regions,
      cluster: stage.cluster,
    });
  };

  public render() {
    const { accounts } = this.state;
    const { application, pipeline, stage } = this.props;
    const { capacity, diskQuota, memory, target } = stage;
    const instanceCount = capacity.desired;
    const { TargetSelect } = NgReact;
    return (
      <div className="cloudfoundry-resize-asg-stage form-horizontal">
        {!pipeline.strategy && (
          <AccountRegionClusterSelector
            accounts={accounts}
            application={application}
            cloudProvider={'cloudfoundry'}
            onComponentUpdate={this.componentUpdated}
            component={stage}
          />
        )}
        <StageConfigField label="Target">
          <TargetSelect model={{ target }} options={StageConstants.TARGET_LIST} onChange={this.targetUpdated} />
        </StageConfigField>
        <div className="form-group">
          <span className="col-md-3 sm-label-right" />
          <div className="col-md-9">
            <div className="col-md-3 sm-label-left">Instances</div>
            <div className="col-md-3 sm-label-left">Mem (MB)</div>
            <div className="col-md-3 sm-label-left">Disk (MB)</div>
          </div>
          <StageConfigField label="Match capacity">
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
              <em className="subinput-note">Scaled capacity will match the numbers entered</em>
            </div>
          </StageConfigField>
        </div>
      </div>
    );
  }
}

import * as React from 'react';

import { Observable, Subject } from 'rxjs';

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

import { AccountRegionClusterSelector } from 'cloudfoundry/presentation';

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
  serviceInstanceName: string;
  target: string;
}

export class CloudfoundryDestroyAsgStageConfig extends React.Component<
  ICloudfoundryDestroyAsgStageProps,
  ICloudfoundryDestroyAsgStageConfigState
> {
  private destroy$ = new Subject();
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
      serviceInstanceName: props.stage.serviceInstanceName,
      target: props.stage.target,
    };
  }

  public componentDidMount(): void {
    Observable.fromPromise(AccountService.listAccounts('cloudfoundry'))
      .takeUntil(this.destroy$)
      .subscribe(accounts => {
        this.setState({ accounts });
        this.accountUpdated();
      });
    this.props.stageFieldUpdated();
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private accountUpdated = (): void => {
    const { credentials } = this.props.stage;
    if (credentials) {
      Observable.fromPromise(AccountService.getRegionsForAccount(credentials))
        .takeUntil(this.destroy$)
        .subscribe(regions => this.setState({ regions }));
    }
  };

  private targetUpdated = (target: string) => {
    this.setState({ target });
    this.props.stage.target = target;
    this.props.stageFieldUpdated();
  };

  private componentUpdated = (stage: any): void => {
    this.props.stage.credentials = stage.credentials;
    this.props.stage.regions = stage.regions;
    this.props.stage.cluster = stage.cluster;
    this.props.stageFieldUpdated();
  };

  public render() {
    const { stage } = this.props;
    const { accounts, application, pipeline, target } = this.state;
    const { TargetSelect } = NgReact;
    return (
      <div className="form-horizontal">
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
      </div>
    );
  }
}

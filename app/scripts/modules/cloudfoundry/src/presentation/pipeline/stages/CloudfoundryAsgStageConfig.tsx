import * as React from 'react';

import { Observable, Subject } from 'rxjs';

import {
  AccountService,
  IAccount,
  IStageConfigProps,
  NgReact,
  StageConstants,
  StageConfigField,
} from '@spinnaker/core';

import { AccountRegionClusterSelector } from 'cloudfoundry/presentation';

export interface ICloudfoundryAsgStageConfigState {
  accounts: IAccount[];
}

export class CloudfoundryAsgStageConfig extends React.Component<IStageConfigProps, ICloudfoundryAsgStageConfigState> {
  private destroy$ = new Subject();

  constructor(props: IStageConfigProps) {
    super(props);
    this.props.updateStageField({ cloudProvider: 'cloudfoundry' });
    this.state = { accounts: [] };
  }

  public componentDidMount(): void {
    Observable.fromPromise(AccountService.listAccounts('cloudfoundry'))
      .takeUntil(this.destroy$)
      .subscribe(accounts => this.setState({ accounts }));
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

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
    const { application, pipeline, stage } = this.props;
    const { target } = stage;
    const { accounts } = this.state;
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

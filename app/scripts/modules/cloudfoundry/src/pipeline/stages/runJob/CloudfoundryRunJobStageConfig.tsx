import * as React from 'react';
import { Observable, Subject } from 'rxjs';

import {
  AccountService,
  IAccount,
  IStageConfigProps,
  NgReact,
  StageConfigField,
  StageConstants,
  TextInput,
} from '@spinnaker/core';

import { AccountRegionClusterSelector } from 'cloudfoundry/presentation';

export interface ICloudfoundryRunTaskStageConfigState {
  accounts: IAccount[];
  region: string;
}

export class CloudfoundryRunJobStageConfig extends React.Component<
  IStageConfigProps,
  ICloudfoundryRunTaskStageConfigState
> {
  private destroy$ = new Subject();

  constructor(props: IStageConfigProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    this.state = {
      accounts: [],
      region: '',
    };
  }

  public componentDidMount(): void {
    Observable.fromPromise(AccountService.listAccounts('cloudfoundry'))
      .takeUntil(this.destroy$)
      .subscribe(accounts => this.setState({ accounts }));
    this.props.stageFieldUpdated();
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private componentUpdated = (stage: any): void => {
    this.props.updateStageField({
      credentials: stage.credentials,
      region: stage.region,
      cluster: stage.cluster,
    });
  };

  private commandUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.updateStageField({ command: event.target.value });
  };

  private JobNameUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.updateStageField({ jobName: event.target.value });
  };

  private targetUpdated = (target: string) => {
    this.props.updateStageField({ target });
  };

  public render() {
    const { application, stage } = this.props;
    const { command, jobName, target } = stage;
    const { accounts } = this.state;
    const { TargetSelect } = NgReact;

    return (
      <div className="cloudfoundry-resize-asg-stage form-horizontal">
        <AccountRegionClusterSelector
          accounts={accounts}
          application={application}
          cloudProvider={'cloudfoundry'}
          onComponentUpdate={this.componentUpdated}
          component={stage}
          isSingleRegion={true}
        />
        <StageConfigField label="Target">
          <TargetSelect model={{ target }} options={StageConstants.TARGET_LIST} onChange={this.targetUpdated} />
        </StageConfigField>
        <StageConfigField label="Command">
          <TextInput type="text" className="form-control" onChange={this.commandUpdated} value={command} />
        </StageConfigField>
        <StageConfigField label="Job Name">
          <TextInput type="text" className="form-control" onChange={this.JobNameUpdated} value={jobName} />
        </StageConfigField>
      </div>
    );
  }
}

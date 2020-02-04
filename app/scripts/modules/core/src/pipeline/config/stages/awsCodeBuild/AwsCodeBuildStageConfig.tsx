import * as React from 'react';

import { Observable, Subject } from 'rxjs';

import { Option } from 'react-select';

import { IgorService, IStageConfigProps, ReactSelectInput, StageConfigField, TextInput } from 'core';

export interface IAwsCodeBuildStageConfigState {
  accounts: string[];
}

export class AwsCodeBuildStageConfig extends React.Component<IStageConfigProps, IAwsCodeBuildStageConfigState> {
  private destroy$ = new Subject();

  public constructor(props: IStageConfigProps) {
    super(props);
    this.state = {
      accounts: [],
    };
  }

  public componentDidMount = (): void => {
    Observable.fromPromise(IgorService.getCodeBuildAccounts())
      .takeUntil(this.destroy$)
      .subscribe((accounts: string[]) => this.setState({ accounts }));
  };

  public componentWillUnmount = (): void => {
    this.destroy$.next();
  };

  private onProjectNameChange = (projectName: string) => {
    this.props.updateStageField({ projectName });
  };

  private accountUpdated = (option: Option<string>) => {
    const account = option.target.value;
    this.props.updateStageField({ account });
  };

  public render() {
    const { stage } = this.props;
    return (
      <div>
        <StageConfigField label="Account">
          <ReactSelectInput
            clearable={false}
            onChange={this.accountUpdated}
            value={stage.account}
            stringOptions={this.state.accounts}
          />
        </StageConfigField>
        <StageConfigField label="Project Name">
          {/* TODO: Select project from a drop-down list. Behind the scene, gate calls igor to fetch projects list */}
          <TextInput
            type="text"
            className="form-control"
            onChange={(evt: any) => this.onProjectNameChange(evt.target.value)}
            value={stage.projectName}
          />
        </StageConfigField>
      </div>
    );
  }
}

import * as React from 'react';
import { cloneDeep } from 'lodash';
import { Observable, Subject } from 'rxjs';

import { AccountService, FormikStageConfig, IAccountDetails, IStage, IStageConfigProps } from '@spinnaker/core';

import { DeployManifestStageForm } from './DeployManifestStageForm';
import { defaultTrafficManagementConfig } from './ManifestDeploymentOptions';

interface IDeployManifestStageConfigState {
  accounts: IAccountDetails[];
}

export class DeployManifestStageConfig extends React.Component<IStageConfigProps, IDeployManifestStageConfigState> {
  private stage: IStage;
  private destroy$ = new Subject();

  public constructor(props: IStageConfigProps) {
    super(props);
    this.state = {
      accounts: [],
    };
    const { stage: initialStageConfig } = props;
    const stage = cloneDeep(initialStageConfig);
    if (!stage.source) {
      stage.source = 'text';
    }
    if (!stage.skipExpressionEvaluation) {
      stage.skipExpressionEvaluation = false;
    }
    if (!stage.trafficManagement) {
      stage.trafficManagement = defaultTrafficManagementConfig;
    }
    if (!stage.cloudProvider) {
      stage.cloudProvider = 'kubernetes';
    }
    if (!stage.moniker) {
      stage.moniker = {};
    }
    if (!stage.moniker.app) {
      stage.moniker.app = props.application.name;
    }
    // Intentionally initializing the stage config only once in the constructor
    // The stage config is then completely owned within FormikStageConfig's Formik state
    this.stage = stage;
  }

  public componentDidMount() {
    this.fetchAccounts();
  }

  private fetchAccounts = (): void => {
    Observable.fromPromise(AccountService.getAllAccountDetailsForProvider('kubernetes', 'v2'))
      .takeUntil(this.destroy$)
      .subscribe((accounts: IAccountDetails[]) => {
        this.setState({ accounts });
      });
  };

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public render() {
    return (
      <FormikStageConfig
        {...this.props}
        stage={this.stage}
        onChange={this.props.updateStage}
        render={props => (
          <DeployManifestStageForm
            {...props}
            accounts={this.state.accounts}
            updatePipeline={this.props.updatePipeline}
          />
        )}
      />
    );
  }
}

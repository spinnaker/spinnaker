import React from 'react';

import { AccountService, Registry, StageConfigField, StageConstants } from '@spinnaker/core';

import { AzureAccountRegionClusterSelector } from '../AzureAccountRegionClusterSelector';

export function AzureDestroyAsgExecutionLabel({ stage }: any) {
  const context = stage.masterStage?.context || stage.context || {};

  return (
    <span className="task-label">
      {' '}
      Destroy Server Group: {context.serverGroupName} ({context.region}){' '}
    </span>
  );
}

export class AzureDestroyAsgStageConfig extends React.Component<any> {
  private mounted = false;

  public componentDidMount(): void {
    this.mounted = true;
    this.applyDefaults();
    this.loadRegionForAccount(this.props.stage.credentials);
  }

  public componentDidUpdate(previousProps: any): void {
    if (previousProps.stage.credentials !== this.props.stage.credentials) {
      this.updateStageField({ regions: [] });
      this.loadRegionForAccount(this.props.stage.credentials);
    }
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private updateStageField = (changes: any): void => {
    Object.assign(this.props.stage, changes);
    this.props.updateStageField(changes);
  };

  private applyDefaults(): void {
    const { application, stage } = this.props;
    stage.regions = stage.regions || [];
    stage.cloudProvider = 'azure';
    stage.interestingHealthProviderNames = stage.interestingHealthProviderNames || [];

    if (!stage.credentials && application.defaultCredentials.azure) {
      stage.credentials = application.defaultCredentials.azure;
    }
    if (!stage.target) {
      stage.target = StageConstants.TARGET_LIST[0].val;
    }
  }

  private loadRegionForAccount(credentials: string): void {
    if (!credentials) {
      return;
    }

    AccountService.getAccountDetails(credentials)
      .then((details: any) => {
        if (this.mounted && this.props.stage.credentials === credentials && details?.org) {
          this.updateStageField({ regions: [details.org] });
        }
      })
      .catch(() => {});
  }

  private accountUpdated = (credentials: string): void => {
    this.loadRegionForAccount(credentials);
  };

  public render() {
    this.applyDefaults();
    const { application, pipeline, stage } = this.props;

    return (
      <div className="form-horizontal">
        {!pipeline?.strategy && (
          <AzureAccountRegionClusterSelector
            application={application}
            stage={stage}
            updateStageField={this.updateStageField}
            onAccountUpdate={this.accountUpdated}
          />
        )}
        <StageConfigField label="Target">
          <select
            className="form-control input-sm"
            value={stage.target}
            onChange={(event) => this.updateStageField({ target: event.target.value })}
          >
            {StageConstants.TARGET_LIST.map((target: any) => (
              <option key={target.val} value={target.val}>
                {target.label}
              </option>
            ))}
          </select>
        </StageConfigField>
      </div>
    );
  }
}

export function registerAzureDestroyAsgStage() {
  Registry.pipeline.registerStage({
    key: 'destroyServerGroup',
    provides: 'destroyServerGroup',
    cloudProvider: 'azure',
    component: AzureDestroyAsgStageConfig,
    executionLabelComponent: AzureDestroyAsgExecutionLabel,
    accountExtractor: (stage: any) => [stage.context.credentials],
    configAccountExtractor: (stage: any) => [stage.credentials],
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
      },
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  } as any);
}

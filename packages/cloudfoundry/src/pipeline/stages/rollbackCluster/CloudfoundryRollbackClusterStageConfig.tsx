import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { AccountService, IAccount, IPipeline, IStageConfigProps, StageConfigField } from '@spinnaker/core';
import { AccountRegionClusterSelector } from '../../../presentation';

export interface ICloudfoundryRollbackClusterStageProps extends IStageConfigProps {
  pipeline: IPipeline;
}

export interface ICloudfoundryRollbackClusterStageConfigState {
  accounts: IAccount[];
}

export class CloudfoundryRollbackClusterStageConfig extends React.Component<
  ICloudfoundryRollbackClusterStageProps,
  ICloudfoundryRollbackClusterStageConfigState
> {
  private destroy$ = new Subject();

  constructor(props: ICloudfoundryRollbackClusterStageProps) {
    super(props);

    this.props.updateStageField({
      cloudProvider: 'cloudfoundry',
      regions: this.props.stage.regions || [],
      targetHealthyRollbackPercentage: 100,
    });

    this.state = { accounts: [] };
  }

  public componentDidMount(): void {
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((accounts) => this.setState({ accounts }));
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private waitTimeBetweenRegionsUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const time = parseInt(event.target.value || '0', 10);
    this.props.updateStageField({ waitTimeBetweenRegions: time });
  };

  private componentUpdate = (stage: any): void => {
    this.props.updateStageField({
      credentials: stage.credentials,
      regions: stage.regions,
      cluster: stage.cluster,
      moniker: stage.moniker,
    });
  };

  public render() {
    const { application, pipeline, stage } = this.props;
    const { waitTimeBetweenRegions } = stage;
    const { accounts } = this.state;
    return (
      <div className="form-horizontal">
        {!pipeline.strategy && (
          <AccountRegionClusterSelector
            accounts={accounts}
            application={application}
            cloudProvider={'cloudfoundry'}
            onComponentUpdate={this.componentUpdate}
            component={stage}
          />
        )}

        {stage.regions.length > 1 && (
          <StageConfigField label="Wait">
            <input
              type="number"
              min="0"
              value={waitTimeBetweenRegions}
              onChange={this.waitTimeBetweenRegionsUpdated}
              className="form-control input-sm inline-number"
            />
            &nbsp;seconds between regional rollbacks.
          </StageConfigField>
        )}
      </div>
    );
  }
}

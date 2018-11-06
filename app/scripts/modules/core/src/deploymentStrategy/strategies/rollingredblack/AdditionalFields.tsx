import * as React from 'react';

import { IDeploymentStrategyAdditionalFieldsProps } from 'core/deploymentStrategy/deploymentStrategy.registry';
import { HelpField } from 'core/help/HelpField';
import { NgReact } from 'core/reactShims';
import { IServerGroupCommand } from 'core/serverGroup';

import { PipelineSelector } from '../PipelineSelector';

export interface IRollingRedBlackCommand extends IServerGroupCommand {
  delayBeforeDisableSec: string;
  pipelineBeforeCleanup: {
    application: string;
  };
  targetPercentages: number[] | string;
}

export interface IRollingRedBlackStrategyAdditionalFieldsProps extends IDeploymentStrategyAdditionalFieldsProps {
  command: IRollingRedBlackCommand;
}

export class AdditionalFields extends React.Component<IRollingRedBlackStrategyAdditionalFieldsProps> {
  private targetPercentagesChange = (model: number[] | string) => {
    this.props.command.targetPercentages = model;
    this.forceUpdate();
  };

  private delayChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.delayBeforeDisableSec = e.target.value;
    this.forceUpdate();
  };

  public render() {
    const { NumberList } = NgReact;
    const { command } = this.props;
    return (
      <div className="form-group">
        <div className="col-md-12 checkbox" style={{ marginTop: 0 }}>
          <label>
            <input type="checkbox" ng-model="$ctrl.command.rollback.onFailure" />
            Rollback to previous server group if deployment fails <HelpField id="strategy.rollingRedBlack.rollback" />
          </label>
        </div>
        <div className="col-md-6" style={{ marginTop: '5px' }}>
          <h4>
            Percentages
            <HelpField id="strategy.rollingRedBlack.targetPercentages" />
          </h4>
          <NumberList model={command.targetPercentages} label="percentage" onChange={this.targetPercentagesChange} />
        </div>
        <div className="col-md-12" style={{ marginTop: '15px' }}>
          <h4>Before Disable</h4>
        </div>

        <div className="col-md-12 form-inline">
          <label>
            Wait
            <HelpField content="Time to wait before disabling instances in old server group" />
          </label>
          <input
            className="form-control input-sm"
            style={{ width: '60px' }}
            min="0"
            type="number"
            value={command.delayBeforeDisableSec}
            onChange={this.delayChange}
            placeholder="0"
          />
          seconds
        </div>
        <div className="col-md-12" style={{ marginTop: '5px' }}>
          <label>
            Run a Pipeline
            <HelpField content="Pipeline to run before disabling instances in old server group" />
          </label>
          <PipelineSelector command={command.pipelineBeforeCleanup} type="pipelines" />
        </div>
      </div>
    );
  }
}

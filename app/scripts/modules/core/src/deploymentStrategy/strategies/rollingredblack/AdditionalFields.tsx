import { set } from 'lodash';
import React from 'react';

import { PipelineSelector } from '../PipelineSelector';
import { IDeploymentStrategyAdditionalFieldsProps } from '../../deploymentStrategy.registry';
import { NumberList } from '../../../forms';
import { HelpField } from '../../../help/HelpField';
import { IServerGroupCommand } from '../../../serverGroup';

export interface IRollingRedBlackCommand extends IServerGroupCommand {
  delayBeforeDisableSec: string;
  delayBeforeScaleDownSec: string;
  pipelineBeforeCleanup: {
    application: string;
  };
  rollback: {
    onFailure: boolean;
  };
  scaleDown: boolean;
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

  private scaleDownChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.scaleDown = e.target.checked;
    this.forceUpdate();
  };

  private rollbackOnFailureChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.rollback.onFailure = e.target.checked;
    this.forceUpdate();
  };

  private handleChange = (key: string, value: string) => {
    set(this.props.command, key, value);
    this.forceUpdate();
  };

  public render() {
    const { command } = this.props;
    const rollbackOnFailure = command.rollback && command.rollback.onFailure;
    return (
      <div className="form-group">
        <div className="col-md-12 checkbox" style={{ marginTop: 0 }}>
          <label>
            <input type="checkbox" checked={rollbackOnFailure} onChange={this.rollbackOnFailureChange} />
            Rollback to previous server group if deployment fails <HelpField id="strategy.rollingRedBlack.rollback" />
          </label>
        </div>
        <div className="col-md-12 checkbox">
          <label>
            <input type="checkbox" checked={command.scaleDown} onChange={this.scaleDownChange} />
            Scale down replaced server groups to zero instances <HelpField id="strategy.redblack.scaleDown" />
          </label>
        </div>

        {command.scaleDown && (
          <div className="col-md-12 form-inline" style={{ marginTop: '5px' }}>
            <label>
              <span style={{ marginRight: '2px' }}>Wait Before Scale Down</span>
              <HelpField content="Time to wait before scaling down all old server groups" />
            </label>
            <input
              className="form-control input-sm"
              style={{ width: '60px', marginLeft: '2px', marginRight: '2px' }}
              min="0"
              type="number"
              value={command.delayBeforeScaleDownSec}
              onChange={(e) => this.handleChange('delayBeforeScaleDownSec', e.target.value)}
              placeholder="0"
            />
            seconds
          </div>
        )}

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
            <HelpField content="Time to wait after each percentage step before disabling instances in the old server group" />
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
            <HelpField content="Pipeline to run after each percentage step before disabling instances in the old server group" />
          </label>
          <PipelineSelector command={command.pipelineBeforeCleanup} type="pipelines" />
        </div>
      </div>
    );
  }
}

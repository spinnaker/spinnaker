import React from 'react';

import type { IDeploymentStrategyAdditionalFieldsProps } from '../../deploymentStrategy.registry';
import { HelpField } from '../../../help/HelpField';
import type { IRedBlackCommand } from './redblack.strategy';

export interface IRedBlackStrategyAdditionalFieldsProps extends IDeploymentStrategyAdditionalFieldsProps {
  command: IRedBlackCommand;
}

export class AdditionalFields extends React.Component<IRedBlackStrategyAdditionalFieldsProps> {
  private rollbackOnFailureChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.rollback.onFailure = e.target.checked;
    this.forceUpdate();
  };

  private scaleDownChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.scaleDown = e.target.checked;
    this.forceUpdate();
  };

  private maxRemainingAsgsChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.maxRemainingAsgs = parseInt(e.target.value, 10);
    this.forceUpdate();
  };

  private delayBeforeDisableSecChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.delayBeforeDisableSec = parseInt(e.target.value, 10);
    this.forceUpdate();
  };

  private delayBeforeScaleDownSecChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.delayBeforeScaleDownSec = parseInt(e.target.value, 10);
    this.forceUpdate();
  };

  public render() {
    const { command } = this.props;
    return (
      <div className="form-group">
        <div className="col-md-12 checkbox" style={{ marginTop: 0 }}>
          <label>
            <input
              type="checkbox"
              checked={command.rollback?.onFailure ?? false}
              onChange={this.rollbackOnFailureChange}
            />
            Rollback to previous server group if deployment fails <HelpField id="strategy.redblack.rollback" />
          </label>
        </div>
        <div className="col-md-12 checkbox">
          <label>
            <input type="checkbox" checked={command.scaleDown} onChange={this.scaleDownChange} />
            Scale down replaced server groups to zero instances <HelpField id="strategy.redblack.scaleDown" />
          </label>
        </div>
        <div className="col-md-12 form-inline">
          <label>
            Maximum number of server groups to leave
            <HelpField id="strategy.redblack.maxRemainingAsgs" />
          </label>
          <input
            className="form-control input-sm"
            style={{ width: '50px' }}
            type="number"
            value={command.maxRemainingAsgs}
            onChange={this.maxRemainingAsgsChange}
            min="2"
          />
        </div>
        <div className="col-md-12 form-inline">
          <label>
            Wait Before Disable
            <HelpField content="Time to wait before disabling all old server groups in this cluster" />
          </label>
          <input
            className="form-control input-sm"
            style={{ width: '60px' }}
            min="0"
            type="number"
            value={command.delayBeforeDisableSec}
            onChange={this.delayBeforeDisableSecChange}
            placeholder="0"
          />
          seconds
        </div>
        {command.scaleDown && (
          <div className="col-md-12 form-inline" style={{ marginTop: '5px' }}>
            <label>
              Wait Before Scale Down
              <HelpField content="Time to wait before scaling down all old server groups in this cluster" />
            </label>
            <input
              className="form-control input-sm"
              style={{ width: '60px' }}
              min="0"
              type="number"
              value={command.delayBeforeScaleDownSec}
              onChange={this.delayBeforeScaleDownSecChange}
              placeholder="0"
            />
            seconds
          </div>
        )}
      </div>
    );
  }
}

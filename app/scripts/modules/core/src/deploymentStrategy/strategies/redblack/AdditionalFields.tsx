import React from 'react';
import { set } from 'lodash';

import { IDeploymentStrategyAdditionalFieldsProps } from '../../deploymentStrategy.registry';
import { HelpField } from 'core/help/HelpField';

import { IRedBlackCommand } from './redblack.strategy';

export interface IRedBlackStrategyAdditionalFieldsProps extends IDeploymentStrategyAdditionalFieldsProps {
  command: IRedBlackCommand;
}

export class AdditionalFields extends React.Component<IRedBlackStrategyAdditionalFieldsProps> {
  private handleChange = (key: string, value: any) => {
    set(this.props.command, key, value);
    this.forceUpdate();
  };

  public render() {
    const { command } = this.props;
    return (
      <div className="form-group">
        <div className="col-md-12 checkbox">
          <label>
            <input
              type="checkbox"
              checked={command.rollback.onFailure}
              onChange={e => this.handleChange('rollback.onFailure', e.target.checked)}
            />
            Rollback to previous server group if deployment fails <HelpField id="strategy.redblack.rollback" />
          </label>
        </div>
        <div className="col-md-12 checkbox">
          <label>
            <input
              type="checkbox"
              checked={command.scaleDown}
              onChange={e => this.handleChange('scaleDown', e.target.checked)}
            />
            Scale down replaced server groups to zero instances
            <HelpField id="strategy.redblack.scaleDown" />
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
            onChange={e => this.handleChange('maxRemainingAsgs', e.target.value)}
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
            onChange={e => this.handleChange('delayBeforeDisableSec', e.target.value)}
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
              onChange={e => this.handleChange('delayBeforeScaleDownSec', e.target.value)}
              placeholder="0"
            />
            seconds
          </div>
        )}
      </div>
    );
  }
}

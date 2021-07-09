import React from 'react';

import { HelpField, IDeploymentStrategyAdditionalFieldsProps } from '@spinnaker/core';

import { AWSProviderSettings } from '../aws.settings';
import { IRollingPushCommand } from './rollingPush.strategy';

export interface IRollingPushStrategyAdditionalFieldsProps extends IDeploymentStrategyAdditionalFieldsProps {
  command: IRollingPushCommand;
}

export class AdditionalFields extends React.Component<IRollingPushStrategyAdditionalFieldsProps> {
  private handleChange = (key: string, value: any) => {
    this.props.command.termination[key] = value;
    this.forceUpdate();
  };

  public render() {
    const { command } = this.props;
    return (
      <div className="form-group" style={{ marginTop: '20px' }}>
        <div className="well-compact alert alert-warning">
          <strong>Note:</strong> a rolling push only updates the{' '}
          <em>
            launch {Boolean(AWSProviderSettings.serverGroups?.enableLaunchTemplates) ? 'template' : 'configuration'}
          </em>{' '}
          for the auto scaling group.
          <br /> Changes to the following fields will be ignored:
          <ul>
            <li>Account, Region, Subnet Type</li>
            <li>Capacity</li>
            <li>Load Balancers</li>
            <li>Health Check Configuration</li>
            <li>Termination Policies, Enable Traffic flag</li>
          </ul>
        </div>
        <div className="form-group">
          <div className="col-md-12 checkbox">
            <label>
              <input
                type="checkbox"
                checked={command.termination.relaunchAllInstances}
                onChange={(e) => this.handleChange('relaunchAllInstances', e.target.checked)}
              />
              <b>Relaunch all instances</b>
              <HelpField id="strategy.rollingPush.relaunchAll" />
            </label>
          </div>
        </div>

        {!command.termination.relaunchAllInstances && (
          <div className="form-group">
            <div className="col-md-5 sm-label-right">
              Total relaunches
              <HelpField id="strategy.rollingPush.totalRelaunches" />
            </div>
            <div className="col-md-2">
              <input
                className="form-control input-sm"
                type="number"
                value={command.termination.totalRelaunches}
                onChange={(e) => this.handleChange('totalRelaunches', e.target.value)}
                min="0"
              />
            </div>
          </div>
        )}

        {(command.termination.totalRelaunches > 0 || command.termination.relaunchAllInstances) && (
          <div className="form-group">
            <div className="col-md-5 sm-label-right">
              Concurrent relaunches
              <HelpField id="strategy.rollingPush.concurrentRelaunches" />
            </div>
            <div className="col-md-2">
              <input
                className="form-control input-sm"
                type="number"
                value={command.termination.concurrentRelaunches}
                onChange={(e) => this.handleChange('concurrentRelaunches', e.target.value)}
                min="1"
              />
            </div>
          </div>
        )}

        {(command.termination.totalRelaunches > 0 || command.termination.relaunchAllInstances) && (
          <div className="form-group">
            <div className="col-md-5 sm-label-right">
              Order
              <HelpField id="strategy.rollingPush.order" />
            </div>
            <div className="col-md-3">
              <select
                className="input input-sm"
                style={{ width: '100px' }}
                value={command.termination.order}
                onChange={(e) => this.handleChange('order', e.target.value)}
              >
                <option value="oldest">oldest first</option>
                <option value="newest">newest first</option>
              </select>
            </div>
          </div>
        )}
      </div>
    );
  }
}

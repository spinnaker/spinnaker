import { set } from 'lodash';
import React from 'react';
import Select, { Option } from 'react-select';

import { IDeploymentStrategyAdditionalFieldsProps } from '../../deploymentStrategy.registry';
import { NumberList } from '../../../forms';
import { HelpField } from '../../../help/HelpField';
import {
  DeploymentMonitorReader,
  IDeploymentMonitorDefinition,
} from '../../../pipeline/config/stages/monitoreddeploy/DeploymentMonitorReader';
import { IServerGroupCommand } from '../../../serverGroup';

export interface IMonitoredDeployCommand extends IServerGroupCommand {
  delayBeforeScaleDownSec: string;
  failureActions: {
    destroyInstances: boolean;
    rollback: string;
  };
  maxRemainingAsgs: number;
  scaleDown: boolean;
  deploySteps: number[] | string;
  deploymentMonitor: {
    id: string;
    parameters: {};
  };
}

export enum RollbackType {
  None = 'None',
  Automatic = 'Automatic',
  Manual = 'Manual',
}

export interface IMonitoredDeployStrategyAdditionalFieldsProps extends IDeploymentStrategyAdditionalFieldsProps {
  command: IMonitoredDeployCommand;
}

export interface IMonitoredDeployStrategyAdditionalFieldsState {
  deploymentMonitors: IDeploymentMonitorDefinition[];
}

export class AdditionalFields extends React.Component<
  IMonitoredDeployStrategyAdditionalFieldsProps,
  IMonitoredDeployStrategyAdditionalFieldsState
> {
  public state: IMonitoredDeployStrategyAdditionalFieldsState = {
    deploymentMonitors: [],
  };

  public componentDidMount() {
    DeploymentMonitorReader.getDeploymentMonitors().then((deploymentMonitors) => {
      this.setState({ deploymentMonitors });
    });
  }

  private deployStepsChange = (model: number[] | string) => {
    this.props.command.deploySteps = model;
    this.forceUpdate();
  };

  private scaleDownChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.scaleDown = e.target.checked;
    this.forceUpdate();
  };

  private rollbackOnFailureChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.failureActions.rollback = e.target.checked ? RollbackType.Automatic : RollbackType.None;

    if (this.props.command.failureActions.rollback === RollbackType.None) {
      this.props.command.failureActions.destroyInstances = false;
    }
    this.forceUpdate();
  };

  private destroyFailedAsgChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.failureActions.destroyInstances = e.target.checked;
    this.forceUpdate();
  };

  private maxRemainingAsgsChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.command.maxRemainingAsgs = parseInt(e.target.value, 10);
    this.forceUpdate();
  };

  private handleDeploymentMonitorChange = (option: Option<string>) => {
    this.props.command.deploymentMonitor.id = option.value;
    this.forceUpdate();
  };

  private handleChange = (key: string, value: string) => {
    set(this.props.command, key, value);
    this.forceUpdate();
  };

  public render() {
    const { command } = this.props;
    const rollbackOnFailure = command.failureActions && command.failureActions.rollback === RollbackType.Automatic;
    const destroyFailedAsg = command.failureActions && command.failureActions.destroyInstances;

    return (
      <div className="form-group">
        {this.state.deploymentMonitors && (
          <div className="col-md-10">
            <Select
              clearable={false}
              required={true}
              options={this.state.deploymentMonitors.map((deploymentMonitor) => ({
                label: deploymentMonitor?.name || '',
                value: deploymentMonitor?.id || '',
              }))}
              placeholder="select deployment monitor"
              value={command.deploymentMonitor?.id || ''}
              onChange={this.handleDeploymentMonitorChange}
            />
          </div>
        )}
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
        <div className="col-md-12 checkbox" style={{ marginTop: 0 }}>
          <label>
            <input type="checkbox" checked={rollbackOnFailure} onChange={this.rollbackOnFailureChange} />
            Rollback to previous server group if deployment fails <HelpField id="strategy.monitored.rollback" />
          </label>
        </div>
        <div className="col-md-12 checkbox" style={{ marginTop: 0 }}>
          <label>
            <input
              type="checkbox"
              checked={destroyFailedAsg}
              disabled={!rollbackOnFailure}
              onChange={this.destroyFailedAsgChange}
            />
            Destroy the failed server group after rollback <HelpField id="strategy.monitored.destroyFailedAsg" />
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
            <HelpField id="strategy.monitored.deploySteps" />
          </h4>
          <NumberList model={command.deploySteps} label="percentage" onChange={this.deployStepsChange} />
        </div>
      </div>
    );
  }
}

import * as React from 'react';
import Select, { Option } from 'react-select';
import { set } from 'lodash';

import { IDeploymentStrategyAdditionalFieldsProps } from 'core/deploymentStrategy/deploymentStrategy.registry';
import { HelpField } from 'core/help/HelpField';
import { NgReact } from 'core/reactShims';
import { IServerGroupCommand } from 'core/serverGroup';
import {
  DeploymentMonitorReader,
  IDeploymentMonitorDefinition,
} from 'core/pipeline/config/stages/monitoreddeploy/DeploymentMonitorReader';

export interface IMonitoredDeployCommand extends IServerGroupCommand {
  delayBeforeScaleDownSec: string;
  rollback: {
    onFailure: boolean;
  };
  maxRemainingAsgs: number;
  scaleDown: boolean;
  deploySteps: number[] | string;
  deploymentMonitor: {
    id: string;
    parameters: {};
  };
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
    DeploymentMonitorReader.getDeploymentMonitors().then(deploymentMonitors => {
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
    this.props.command.rollback.onFailure = e.target.checked;
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
    const { NumberList } = NgReact;
    const { command } = this.props;
    const rollbackOnFailure = command.rollback && command.rollback.onFailure;

    return (
      <div className="form-group">
        {this.state.deploymentMonitors && (
          <div className="col-md-10">
            <Select
              clearable={false}
              required={true}
              options={this.state.deploymentMonitors.map(deploymentMonitor => ({
                label: deploymentMonitor.name,
                value: deploymentMonitor.id,
              }))}
              placeholder="select deployment monitor"
              value={command.deploymentMonitor.id || ''}
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
              onChange={e => this.handleChange('delayBeforeScaleDownSec', e.target.value)}
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

import { defaultsDeep, unset } from 'lodash';
import React from 'react';
import Select, { Option } from 'react-select';

import {
  CoreRedBlackAdditionalFields,
  HelpField,
  IDeploymentStrategy,
  IDeploymentStrategyAdditionalFieldsProps,
  IServerGroupCommand,
  Markdown,
} from '@spinnaker/core';

import { IRedBlackCommand } from './strategies/redblack/redblack.strategy';
import {
  AdditionalFields as AdditionalRollingRedBlackFields,
  IRollingRedBlackCommand,
} from './strategies/rollingredblack/AdditionalFields';

export interface ICloudFoundryDeploymentStrategySelectorProps {
  command: IServerGroupCommand;
  onFieldChange: (key: string, value: any) => void;
  onStrategyChange: (command: IServerGroupCommand, strategy: IDeploymentStrategy) => void;
}

export interface ICloudFoundryDeploymentStrategySelectorState {
  strategies: IDeploymentStrategy[];
  currentStrategy: string;
  AdditionalFieldsComponent: React.ComponentType<IDeploymentStrategyAdditionalFieldsProps>;
}

export class CloudFoundryDeploymentStrategySelector extends React.Component<
  ICloudFoundryDeploymentStrategySelectorProps,
  ICloudFoundryDeploymentStrategySelectorState
> {
  public state: ICloudFoundryDeploymentStrategySelectorState = {
    strategies: [
      {
        label: 'None',
        description: 'Creates the next server group with no impact on existing server groups',
        key: '',
      },
      {
        label: 'Highlander',
        description:
          'Destroys <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
        key: 'highlander',
      },
      {
        label: 'Red/Black',
        description:
          'Disables <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
        key: 'redblack',
        additionalFields: ['maxRemainingAsgs'],
        AdditionalFieldsComponent: CoreRedBlackAdditionalFields,
        initializationMethod: (command: IRedBlackCommand) => {
          defaultsDeep(command, {
            rollback: {
              onFailure: false,
            },
            maxRemainingAsgs: 2,
            delayBeforeDisableSec: 0,
            delayBeforeScaleDownSec: 0,
            scaleDown: false,
          });
        },
      },
      {
        label: 'Rolling Red/Black',
        description:
          'Gradually replaces <i>all</i> previous server group instances in the cluster as soon as new server group instances pass health checks',
        key: 'cfrollingredblack',
        additionalFields: ['targetPercentages'],
        AdditionalFieldsComponent: AdditionalRollingRedBlackFields,
        initializationMethod: (command: IRollingRedBlackCommand) => {
          defaultsDeep(command, {
            rollback: {
              onFailure: false,
            },
            targetPercentages: command.targetPercentages ? command.targetPercentages : [50, 100], // defaultsDeep does not work with arrays
            delayBeforeDisableSec: 0,
            delayBeforeScaleDownSec: 0,
            maxRemainingAsgs: 2,
            scaleDown: false,
          });
        },
      },
    ],
    currentStrategy: null,
    AdditionalFieldsComponent: undefined,
  };

  public selectStrategy(strategy: string): void {
    const { command, onStrategyChange } = this.props;
    const oldStrategy = this.state.strategies.find((s) => s.key === this.state.currentStrategy);
    const newStrategy = this.state.strategies.find((s) => s.key === strategy);

    if (oldStrategy && oldStrategy.additionalFields) {
      oldStrategy.additionalFields.forEach((field) => {
        if (!newStrategy || !newStrategy.additionalFields || !newStrategy.additionalFields.includes(field)) {
          unset(command, field);
        }
      });
    }

    let AdditionalFieldsComponent;
    if (newStrategy) {
      AdditionalFieldsComponent = newStrategy.AdditionalFieldsComponent;
      if (newStrategy.initializationMethod) {
        newStrategy.initializationMethod(command);
      }
    }
    command.strategy = strategy;
    if (onStrategyChange && newStrategy) {
      onStrategyChange(command, newStrategy);
    }
    this.setState({ currentStrategy: strategy, AdditionalFieldsComponent });
  }

  public strategyChanged = (option: Option<IDeploymentStrategy>) => {
    this.selectStrategy(option.key);
  };

  public componentDidMount() {
    this.selectStrategy(this.props.command.strategy);
  }

  public render() {
    const { command, onFieldChange } = this.props;
    const { AdditionalFieldsComponent, currentStrategy, strategies } = this.state;
    const hasAdditionalFields = Boolean(AdditionalFieldsComponent);
    if (strategies && strategies.length) {
      return (
        <div>
          <div className="StandardFieldLayout flex-container-h baseline margin-between-lg">
            <div className={`sm-label-right`} style={{ paddingLeft: '13px' }}>
              Strategy &nbsp;
              <HelpField id="core.serverGroup.strategy" />
            </div>
            <div className="flex-grow">
              <Select
                clearable={false}
                required={true}
                options={strategies}
                placeholder="None"
                valueKey="key"
                value={currentStrategy}
                optionRenderer={this.strategyOptionRenderer}
                valueRenderer={(o) => <>{o.label}</>}
                onChange={this.strategyChanged}
              />
            </div>
          </div>
          {hasAdditionalFields && (
            <div className="form-group col-md-9 col-md-offset-3" style={{ marginTop: '5px', float: 'right' }}>
              <AdditionalFieldsComponent command={command} onChange={onFieldChange} />
            </div>
          )}
        </div>
      );
    }

    return null;
  }

  private strategyOptionRenderer = (option: IDeploymentStrategy) => {
    return (
      <div className="body-regular">
        <strong>
          <Markdown tag="span" message={option.label} />
        </strong>
        <div>
          <Markdown tag="span" message={option.description} />
        </div>
      </div>
    );
  };
}

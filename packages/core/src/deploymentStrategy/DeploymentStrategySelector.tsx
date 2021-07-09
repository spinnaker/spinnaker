import { unset } from 'lodash';
import React from 'react';
import Select, { Option } from 'react-select';

import {
  DeploymentStrategyRegistry,
  IDeploymentStrategy,
  IDeploymentStrategyAdditionalFieldsProps,
} from './deploymentStrategy.registry';
import { HelpField } from '../help/HelpField';
import { Markdown } from '../presentation';
import { IServerGroupCommand } from '../serverGroup';

export interface IDeploymentStrategySelectorProps {
  command: IServerGroupCommand;
  onFieldChange: (key: string, value: any) => void;
  onStrategyChange: (command: IServerGroupCommand, strategy: IDeploymentStrategy) => void;
  labelColumns?: string;
  fieldColumns?: string;
}

export interface IDeploymentStrategySelectorState {
  strategies: IDeploymentStrategy[];
  currentStrategy: string;
  AdditionalFieldsComponent: React.ComponentType<IDeploymentStrategyAdditionalFieldsProps>;
}

export class DeploymentStrategySelector extends React.Component<
  IDeploymentStrategySelectorProps,
  IDeploymentStrategySelectorState
> {
  public static defaultProps: Partial<IDeploymentStrategySelectorProps> = {
    fieldColumns: '7',
    labelColumns: '3',
  };

  public state: IDeploymentStrategySelectorState = {
    strategies: DeploymentStrategyRegistry.listStrategies(
      this.props.command.selectedProvider || this.props.command.cloudProvider,
    ),
    currentStrategy: null,
    AdditionalFieldsComponent: undefined,
  };

  public selectStrategy(strategy: string, onMount = false): void {
    const { command, onStrategyChange } = this.props;

    const oldStrategy = DeploymentStrategyRegistry.getStrategy(this.state.currentStrategy);
    const newStrategy = DeploymentStrategyRegistry.getStrategy(strategy);

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
      // do not run on mount otherwise we'll confusingly fill in things that weren't there
      if (newStrategy.initializationMethod && !onMount) {
        newStrategy.initializationMethod(command);
      }
    }
    // Usage of the angular <deployment-strategy-selector> do not have an onStrategyChange and simply expect command.strategy to be updated
    // This was previously done by <ui-select ng-model="$ctrl.command.strategy">
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
    this.selectStrategy(this.props.command.strategy, true);
  }

  public render() {
    const { command, fieldColumns, labelColumns, onFieldChange } = this.props;
    const { AdditionalFieldsComponent, currentStrategy, strategies } = this.state;
    const hasAdditionalFields = Boolean(AdditionalFieldsComponent);

    if (strategies && strategies.length) {
      return (
        <div className="form-group">
          <div className={`col-md-${labelColumns} sm-label-right`} style={{ paddingLeft: '13px' }}>
            Strategy
            <HelpField id="core.serverGroup.strategy" />
          </div>
          <div className={`col-md-${fieldColumns}`}>
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
          {hasAdditionalFields && (
            <div className="col-md-9 col-md-offset-3" style={{ marginTop: '5px' }}>
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

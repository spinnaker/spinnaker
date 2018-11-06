import * as React from 'react';
import * as DOMPurify from 'dompurify';
import Select, { Option } from 'react-select';
import { unset } from 'lodash';

import { IServerGroupCommand } from 'core/serverGroup';

import {
  DeploymentStrategyRegistry,
  IDeploymentStrategy,
  IDeploymentStrategyAdditionalFieldsProps,
} from './deploymentStrategy.registry';
import { HelpField } from 'core/help/HelpField';

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

  public selectStrategy(strategy: string): void {
    const { command, onStrategyChange } = this.props;

    const oldStrategy = DeploymentStrategyRegistry.getStrategy(this.state.currentStrategy);
    const newStrategy = DeploymentStrategyRegistry.getStrategy(strategy);

    if (oldStrategy && oldStrategy.additionalFields) {
      oldStrategy.additionalFields.forEach(field => {
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
              valueRenderer={o => <>{o.label}</>}
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
          <span dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(option.label) }} />
        </strong>
        <div>
          <span dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(option.description) }} />
        </div>
      </div>
    );
  };
}

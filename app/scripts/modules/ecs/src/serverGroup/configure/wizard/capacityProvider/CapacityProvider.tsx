import { module } from 'angular';
import React from 'react';
import { Alert } from 'react-bootstrap';
import { Option } from 'react-select';
import { react2angular } from 'react2angular';

import { HelpField, TetheredSelect, withErrorBoundary } from '@spinnaker/core';

import { IEcsCapacityProviderStrategyItem, IEcsServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IEcsCapacityProviderProps {
  command: IEcsServerGroupCommand;
  notifyAngular: (key: string, value: any) => void;
  configureCommand: (query: string) => PromiseLike<void>;
}

interface IEcsCapacityProviderState {
  capacityProviderStrategy: IEcsCapacityProviderStrategyItem[];
  defaultCapacityProviderStrategy: IEcsCapacityProviderStrategyItem[];
  availableCapacityProviders: string[];
  ecsClusterName: string;
  useDefaultCapacityProviders: boolean;
  capacityProviderLoadedFlag: boolean;
}

class EcsCapacityProvider extends React.Component<IEcsCapacityProviderProps, IEcsCapacityProviderState> {
  constructor(props: IEcsCapacityProviderProps) {
    super(props);
    const cmd = this.props.command;

    this.state = {
      availableCapacityProviders: this.getAvailableCapacityProviders(cmd),
      defaultCapacityProviderStrategy: this.getDefaultCapacityProviderStrategy(cmd),
      ecsClusterName: cmd.ecsClusterName,
      useDefaultCapacityProviders:
        cmd.useDefaultCapacityProviders || (cmd.capacityProviderStrategy && cmd.capacityProviderStrategy.length == 0),
      capacityProviderStrategy:
        cmd.capacityProviderStrategy && cmd.capacityProviderStrategy.length > 0 ? cmd.capacityProviderStrategy : [],
      capacityProviderLoadedFlag: false,
    };
  }

  public componentDidMount() {
    this.props.configureCommand('1').then(() => {
      const cmd = this.props.command;
      const useDefaultCapacityProviders = this.state.useDefaultCapacityProviders;
      this.setState({
        availableCapacityProviders: this.getAvailableCapacityProviders(cmd),
        defaultCapacityProviderStrategy: this.getDefaultCapacityProviderStrategy(cmd),
        capacityProviderStrategy: this.getCapacityProviderStrategy(useDefaultCapacityProviders, cmd),
        capacityProviderLoadedFlag: true,
      });
      this.props.notifyAngular('useDefaultCapacityProviders', this.state.useDefaultCapacityProviders);
      this.props.notifyAngular('capacityProviderStrategy', this.state.capacityProviderStrategy);
    });
  }

  private getAvailableCapacityProviders = (cmd: IEcsServerGroupCommand) => {
    return cmd.backingData && cmd.backingData.filtered && cmd.backingData.filtered.availableCapacityProviders
      ? cmd.backingData.filtered.availableCapacityProviders
      : [];
  };

  private getDefaultCapacityProviderStrategy = (cmd: IEcsServerGroupCommand) => {
    return cmd.backingData && cmd.backingData.filtered && cmd.backingData.filtered.defaultCapacityProviderStrategy
      ? cmd.backingData.filtered.defaultCapacityProviderStrategy
      : [];
  };

  private getCapacityProviderStrategy = (useDefaultCapacityProviders: boolean, cmd: IEcsServerGroupCommand) => {
    return useDefaultCapacityProviders &&
      cmd.backingData &&
      cmd.backingData.filtered &&
      cmd.backingData.filtered.defaultCapacityProviderStrategy
      ? cmd.backingData.filtered.defaultCapacityProviderStrategy
      : this.state.capacityProviderStrategy;
  };

  private addCapacityProviderStrategy = () => {
    const capacityProviderStrategy = this.state.capacityProviderStrategy;
    capacityProviderStrategy.push({ capacityProvider: '', base: null, weight: null });
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
    this.setState({ capacityProviderStrategy: capacityProviderStrategy });
  };

  private removeCapacityProviderStrategy = (index: number) => {
    const capacityProviderStrategy = this.state.capacityProviderStrategy;
    capacityProviderStrategy.splice(index, 1);
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
    this.setState({ capacityProviderStrategy: capacityProviderStrategy });
  };

  private updateCapacityProviderName = (index: number, targetCapacityProviderName: string) => {
    const capacityProviderStrategy = this.state.capacityProviderStrategy;
    const targetCapacityProviderStrategy = capacityProviderStrategy[index];
    targetCapacityProviderStrategy.capacityProvider = targetCapacityProviderName;
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
    this.setState({ capacityProviderStrategy: capacityProviderStrategy });
    this.props.command.viewState.dirty.customCapacityProviders = [];
  };

  private updateCapacityProviderBase = (index: number, targetCapacityProviderBase: number) => {
    const capacityProviderStrategy = this.state.capacityProviderStrategy;
    const targetCapacityProviderStrategy = capacityProviderStrategy[index];
    targetCapacityProviderStrategy.base = targetCapacityProviderBase;
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
    this.setState({ capacityProviderStrategy: capacityProviderStrategy });
  };

  private updateCapacityProviderWeight = (index: number, targetCapacityProviderWeight: number) => {
    const capacityProviderStrategy = this.state.capacityProviderStrategy;
    const targetCapacityProviderStrategy = capacityProviderStrategy[index];
    targetCapacityProviderStrategy.weight = targetCapacityProviderWeight;
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
    this.setState({ capacityProviderStrategy: capacityProviderStrategy });
  };

  private updateCapacityProviderType = (targetCapacityProviderType: boolean) => {
    this.setState({ useDefaultCapacityProviders: targetCapacityProviderType });
    this.props.notifyAngular('useDefaultCapacityProviders', targetCapacityProviderType);
    this.props.command.viewState.dirty.customCapacityProviders = [];

    const capacityProviderStrategy =
      targetCapacityProviderType && this.state.defaultCapacityProviderStrategy.length > 0
        ? this.state.defaultCapacityProviderStrategy
        : [];
    this.setState({ capacityProviderStrategy: capacityProviderStrategy });
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
  };

  render(): React.ReactElement<EcsCapacityProvider> {
    const updateCapacityProviderName = this.updateCapacityProviderName;
    const updateCapacityProviderBase = this.updateCapacityProviderBase;
    const updateCapacityProviderWeight = this.updateCapacityProviderWeight;
    const addCapacityProviderStrategy = this.addCapacityProviderStrategy;
    const removeCapacityProviderStrategy = this.removeCapacityProviderStrategy;
    const updateCapacityProviderType = this.updateCapacityProviderType;
    const capacityProviderStrategy = this.state.capacityProviderStrategy;
    const useDefaultCapacityProviders = this.state.useDefaultCapacityProviders;
    const capacityProviderLoadedFlag = this.state.capacityProviderLoadedFlag;
    const customDirtyCapacityProviders =
      this.props.command.viewState.dirty && this.props.command.viewState.dirty.customCapacityProviders
        ? this.props.command.viewState.dirty.customCapacityProviders
        : [];
    const defaultDirtyCapacityProviders =
      this.props.command.viewState.dirty && this.props.command.viewState.dirty.defaulCapacityProviders
        ? this.props.command.viewState.dirty.defaulCapacityProviders
        : [];

    const capacityProviderNames =
      this.state.availableCapacityProviders && this.state.availableCapacityProviders.length > 0
        ? this.state.availableCapacityProviders.map((capacityProviderNames) => {
            return { label: `${capacityProviderNames}`, value: capacityProviderNames };
          })
        : [];

    const defaultCPError =
      useDefaultCapacityProviders && defaultDirtyCapacityProviders ? (
        <div className="alert alert-warning">
          <p className="text-left">
            <i className="fa fa-exclamation-triangle"></i>
            Invalid capacity providers are a part of default capacity provider strategy.
            <br />
            Please click 'Done' to use current default capacity provider strategy (shown below) or switch to using a
            custom strategy.
          </p>
        </div>
      ) : (
        ''
      );

    const dirtyCapacityProviderList = customDirtyCapacityProviders
      ? customDirtyCapacityProviders.map(function (capacityProvider, index) {
          return <li key={index}>{capacityProvider}</li>;
        })
      : '';

    const dirtyCapacityProviderSection =
      useDefaultCapacityProviders && defaultDirtyCapacityProviders.length > 0 ? (
        defaultCPError
      ) : customDirtyCapacityProviders.length > 0 && !useDefaultCapacityProviders ? (
        <div className="alert alert-warning">
          <p>
            <i className="fa fa-exclamation-triangle"></i>
            The following capacity providers could not be found in the selected account/region/cluster and were removed:
          </p>
          <ul>{dirtyCapacityProviderList}</ul>
          <br />
          <p className="text-left">Please select the capacity provider(s) from the dropdown to resolve this error.</p>
        </div>
      ) : (
        ''
      );

    const capacityProviderInputs =
      capacityProviderStrategy.length > 0 ? (
        capacityProviderStrategy.map(function (mapping, index) {
          return (
            <tr key={index}>
              {useDefaultCapacityProviders ? (
                <td>
                  <input
                    data-test-id={'ServerGroup.defaultCapacityProvider.name.' + index}
                    type="string"
                    className="form-control input-sm no-spel"
                    required={true}
                    value={mapping.capacityProvider}
                    disabled={true}
                  />
                </td>
              ) : (
                <td data-test-id={'ServerGroup.customCapacityProvider.name.' + index}>
                  <TetheredSelect
                    placeholder="Select capacity provider"
                    options={capacityProviderNames}
                    value={mapping.capacityProvider}
                    onChange={(e: Option) => {
                      updateCapacityProviderName(index, e.label as string);
                    }}
                    clearable={false}
                  />
                </td>
              )}
              <td>
                <input
                  data-test-id={'ServerGroup.capacityProvider.base.' + index}
                  disabled={useDefaultCapacityProviders}
                  type="number"
                  className="form-control input-sm no-spel"
                  value={mapping.base}
                  onChange={(e) => updateCapacityProviderBase(index, e.target.valueAsNumber)}
                />
              </td>
              <td>
                <input
                  data-test-id={'ServerGroup.capacityProvider.weight.' + index}
                  disabled={useDefaultCapacityProviders}
                  type="number"
                  className="form-control input-sm no-spel"
                  required={true}
                  value={mapping.weight}
                  onChange={(e) => updateCapacityProviderWeight(index, e.target.valueAsNumber)}
                />
              </td>
              {!useDefaultCapacityProviders ? (
                <td>
                  <div className="form-control-static">
                    <a className="btn-link sm-label" onClick={() => removeCapacityProviderStrategy(index)}>
                      <span className="glyphicon glyphicon-trash" />
                      <span className="sr-only">Remove</span>
                    </a>
                  </div>
                </td>
              ) : (
                ''
              )}
            </tr>
          );
        })
      ) : useDefaultCapacityProviders && this.state.capacityProviderStrategy.length == 0 ? (
        <tr>
          <div className="sm-label-left" style={{ width: '200%' }}>
            <Alert color="warning">
              {' '}
              The cluster does not have a default capacity provider strategy defined. Set a default capacity provider
              strategy or use a custom strategy.
            </Alert>
          </div>
        </tr>
      ) : (
        ''
      );

    const newCapacityProviderStrategy =
      this.state.ecsClusterName &&
      this.props.command.credentials &&
      this.props.command.region &&
      !useDefaultCapacityProviders &&
      capacityProviderNames.length > 0 ? (
        <button
          data-test-id="ServerGroup.addCapacityProvider"
          className="btn btn-block btn-sm add-new"
          onClick={addCapacityProviderStrategy}
        >
          <span className="glyphicon glyphicon-plus-sign" />
          Add New Capacity Provider
        </button>
      ) : !useDefaultCapacityProviders && capacityProviderLoadedFlag && capacityProviderNames.length == 0 ? (
        <div className="sm-label-left" style={{ width: '200%' }}>
          <Alert color="warning"> The cluster does not have capacity providers defined. </Alert>
        </div>
      ) : (
        ''
      );

    return (
      <div>
        {(customDirtyCapacityProviders.length > 0 || defaultDirtyCapacityProviders.length > 0) &&
        capacityProviderLoadedFlag ? (
          <div>{dirtyCapacityProviderSection}</div>
        ) : (
          ''
        )}
        <div className="sm-label-left">
          <b>Capacity Provider Strategy</b>
          <HelpField id="ecs.capacityProviderStrategy" /> <br />
          <span>({this.state.ecsClusterName})</span>
        </div>
        <div className="radio">
          <label>
            <input
              data-test-id="ServerGroup.capacityProviders.default"
              type="radio"
              checked={useDefaultCapacityProviders}
              onClick={() => updateCapacityProviderType(true)}
              id="computeOptionsLaunchType1"
            />
            Use cluster default
          </label>
        </div>
        <div className="radio">
          <label>
            <input
              data-test-id="ServerGroup.capacityProviders.custom"
              type="radio"
              checked={!useDefaultCapacityProviders}
              onClick={() => updateCapacityProviderType(false)}
              id="computeOptionsCapacityProviders2"
            />
            Use custom (Advanced)
          </label>
        </div>
        {capacityProviderLoadedFlag ? (
          <table className="table table-condensed packed tags">
            <thead>
              <tr>
                <th style={{ width: '50%' }}>
                  Provider name
                  <HelpField id="ecs.capacityProviderName" />
                </th>
                <th style={{ width: '25%' }}>
                  Base
                  <HelpField id="ecs.capacityProviderBase" />
                </th>
                <th style={{ width: '25%' }}>
                  Weight
                  <HelpField id="ecs.capacityProviderWeight" />
                </th>
              </tr>
            </thead>
            <tbody>{capacityProviderInputs}</tbody>
            <tfoot>
              <tr>
                <td colSpan={4}>{newCapacityProviderStrategy}</td>
              </tr>
            </tfoot>
          </table>
        ) : (
          <div className="load medium">
            <div className="message">Loading capacity providers...</div>
            <div className="bars">
              <div className="bar"></div>
              <div className="bar"></div>
              <div className="bar"></div>
            </div>
          </div>
        )}
      </div>
    );
  }
}

export const ECS_CAPACITY_PROVIDER_REACT = 'spinnaker.ecs.serverGroup.configure.wizard.capacityProvider.react';
module(ECS_CAPACITY_PROVIDER_REACT, []).component(
  'ecsCapacityProviderReact',
  react2angular(withErrorBoundary(EcsCapacityProvider, 'ecsCapacityProviderReact'), [
    'command',
    'notifyAngular',
    'configureCommand',
  ]),
);

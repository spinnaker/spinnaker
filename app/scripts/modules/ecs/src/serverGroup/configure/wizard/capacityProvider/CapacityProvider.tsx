import React from 'react';
import { module } from 'angular';
import { react2angular } from 'react2angular';
import {IEcsCapacityProviderStrategyItem, IEcsServerGroupCommand} from '../../serverGroupConfiguration.service';
import { HelpField, withErrorBoundary } from  '@spinnaker/core';

export interface IEcsCapacityProviderProps {
  command: IEcsServerGroupCommand;
  notifyAngular: (key: string, value: any) => void;
}

interface IEcsCapacityProviderState {
  capacityProviderStrategy: IEcsCapacityProviderStrategyItem[],
}

class EcsCapacityProvider extends React.Component<IEcsCapacityProviderProps, IEcsCapacityProviderState>{
  constructor(props: IEcsCapacityProviderProps) {
    super(props);
    const cmd = this.props.command;

    this.state = {
      capacityProviderStrategy: cmd.capacityProviderStrategy,
    };
  }

  private addCapacityProviderStrategy = () => {
    const capacityProviderStrategy = this.state.capacityProviderStrategy;
    capacityProviderStrategy.push({ capacityProvider: '', base: null, weight: null});
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
    this.setState({ capacityProviderStrategy : capacityProviderStrategy });
  };

  private removeCapacityProviderStrategy = (index: number) => {
    const capacityProviderStrategy = this.state.capacityProviderStrategy;
    capacityProviderStrategy.splice(index, 1);
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
    this.setState({capacityProviderStrategy : capacityProviderStrategy });
  }

  private updateCapacityProviderName = (index: number, targetCapacityProviderName: string) => {
    const capacityProviderStrategy = this.state.capacityProviderStrategy;
    const targetCapacityProviderStrategy = capacityProviderStrategy[index];
    targetCapacityProviderStrategy.capacityProvider = targetCapacityProviderName;
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
    this.setState({ capacityProviderStrategy: capacityProviderStrategy });
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
    targetCapacityProviderStrategy.weight= targetCapacityProviderWeight;
    this.props.notifyAngular('capacityProviderStrategy', capacityProviderStrategy);
    this.setState({ capacityProviderStrategy: capacityProviderStrategy });
  };


  render(): React.ReactElement<EcsCapacityProvider> {

    const updateCapacityProviderName = this.updateCapacityProviderName;
    const updateCapacityProviderBase = this.updateCapacityProviderBase;
    const updateCapacityProviderWeight = this.updateCapacityProviderWeight;
    const addCapacityProviderStrategy = this.addCapacityProviderStrategy;
    const removeCapacityProviderStrategy = this.removeCapacityProviderStrategy;

    const capacityProviderInputs = this.state.capacityProviderStrategy.map(function (mapping, index) {
      return (
        <tr key={index}>
          <td>
            <input
              data-test-id={"capacityProvider.name." +  index }
              type="string"
              className="form-control input-sm no-spel"
              required={true}
              value={mapping.capacityProvider}
              onChange={(e) => updateCapacityProviderName(index, e.target.value)}
            />
          </td>
          <td>
            <input
              data-test-id={"capacityProvider.base." + index }
              type="number"
              className="form-control input-sm no-spel"
              required={true}
              value={mapping.base}
              onChange={(e) => updateCapacityProviderBase(index, e.target.valueAsNumber)}
            />
          </td>
          <td>
            <input
              data-test-id={"capacityProvider.weight." +  index }
              type="number"
              className="form-control input-sm no-spel"
              required={true}
              value={mapping.weight}
              onChange={(e) => updateCapacityProviderWeight(index, e.target.valueAsNumber)}
            />
          </td>
          <td>
            <div className="form-control-static">
              <a className="btn-link sm-label" onClick={() => removeCapacityProviderStrategy(index)}>
                <span className="glyphicon glyphicon-trash" />
                <span className="sr-only">Remove</span>
              </a>
            </div>
          </td>
        </tr>
      );
    });

    const newCapacityProviderStrategy = (
      <button className="btn btn-block btn-sm add-new" onClick={addCapacityProviderStrategy} data-test-id="ServerGroup.addCapacityProvider">
        <span className="glyphicon glyphicon-plus-sign" />
        Add New Capacity Provider
    </button>
    )


    return (
      <div>
      <div className="sm-label-left">
        <b>Capacity Provider Strategy</b>
        <HelpField id="ecs.capacityProviderStrategy" />
      </div>
        <table className="table table-condensed packed tags">
          <thead>
          <th style={{ width: '50%' }}> Provider name <HelpField id="ecs.capacityProviderName" /></th>
          <th style={{ width: '25%' }}> Base <HelpField id="ecs.capacityProviderBase" /></th>
          <th style={{ width: '25%' }}>Weight <HelpField id="ecs.capacityProviderWeight" /></th>
          </thead>
          <tbody>
          {capacityProviderInputs}
          </tbody>
          <tfoot>
          <tr>
            <td colSpan={4}>{newCapacityProviderStrategy}</td>
          </tr>
          </tfoot>
        </table>

      </div>

    );
  }
}

export const ECS_CAPACITY_PROVIDER_REACT = 'spinnaker.ecs.serverGroup.configure.wizard.capacityProvider.react';
module(ECS_CAPACITY_PROVIDER_REACT, []).component(
  'ecsCapacityProviderReact',
  react2angular(withErrorBoundary(EcsCapacityProvider, 'ecsCapacityProviderReact'), ['command', 'notifyAngular']),
);

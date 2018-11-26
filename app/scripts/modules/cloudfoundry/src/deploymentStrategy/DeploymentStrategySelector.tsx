import { DeploymentStrategySelector, IDeploymentStrategySelectorState } from '@spinnaker/core';
import { IRedBlackCommand } from 'cloudfoundry/deploymentStrategy/strategies/redblack/redblack.strategy';
import { defaultsDeep, unset } from 'lodash';
import { AdditionalFields } from './strategies/redblack/AdditionalFields';

export class CloudFoundryDeploymentStrategySelector extends DeploymentStrategySelector {
  public state: IDeploymentStrategySelectorState = {
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
        AdditionalFieldsComponent: AdditionalFields,
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
    ],
    currentStrategy: null,
    AdditionalFieldsComponent: undefined,
  };

  public selectStrategy(strategy: string): void {
    const { command, onStrategyChange } = this.props;
    const oldStrategy = this.state.strategies.find(s => s.key === this.state.currentStrategy);
    const newStrategy = this.state.strategies.find(s => s.key === strategy);

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
    command.strategy = strategy;
    if (onStrategyChange && newStrategy) {
      onStrategyChange(command, newStrategy);
    }
    this.setState({ currentStrategy: strategy, AdditionalFieldsComponent });
  }
}

import type { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import type { Application, ApplicationStateProvider } from '../application';
import { registerApplicationState } from '../application';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { InstanceDetails } from './details/InstanceDetails';
import { MultipleInstancesDetails } from './details/MultipleInstancesDetails';
import { StandaloneInstanceDetails } from './details/StandaloneInstanceDetails';
import type { INestedState } from '../navigation';
import { registerRootState } from '../navigation/rootState.registration';

export const INSTANCE_STATES = 'spinnaker.core.instance.states';

export function getStandaloneInstanceState(): INestedState {
  return {
    name: 'instanceDetails',
    url: '/instance/:provider/:account/:region/:instanceId',
    views: {
      'main@': {
        component: StandaloneInstanceDetails,
        $type: 'react',
      },
    },
    resolve: {
      instance: [
        '$stateParams',
        ($stateParams: StateParams) => {
          return {
            instanceId: $stateParams.instanceId,
            account: $stateParams.account,
            region: $stateParams.region,
            provider: $stateParams.provider,
            noApplication: true,
          };
        },
      ],
      app: [
        (): Application => {
          return ApplicationModelBuilder.createStandaloneApplication('(standalone instance)');
        },
      ],
      overrides: () => {
        return {};
      },
      moniker: (): any => null,
      environment: (): any => null,
    },
    data: {
      pageTitleDetails: {
        title: 'Instance Details',
        nameParam: 'instanceId',
      },
      history: {
        type: 'instances',
      },
    },
  };
}

export function getMultipleInstancesState(): INestedState {
  return {
    name: 'multipleInstances',
    url: '/multipleInstances',
    views: {
      'detail@../insight': {
        component: MultipleInstancesDetails,
        $type: 'react',
      },
    },
    data: {
      pageTitleDetails: {
        title: 'Multiple Instances',
      },
    },
  };
}

module(INSTANCE_STATES, []);

registerApplicationState((applicationStateProvider: ApplicationStateProvider) => {
  const instanceDetails: INestedState = {
    name: 'instanceDetails',
    url: '/instanceDetails/:provider/:instanceId',
    views: {
      'detail@../insight': {
        component: InstanceDetails,
        $type: 'react',
      },
    },
    resolve: {
      overrides: () => {
        return {};
      },
      instance: [
        '$stateParams',
        ($stateParams: StateParams) => {
          return {
            instanceId: $stateParams.instanceId,
          };
        },
      ],
    },
    data: {
      pageTitleDetails: {
        title: 'Instance Details',
        nameParam: 'instanceId',
      },
      history: {
        type: 'instances',
      },
    },
  };

  applicationStateProvider.addInsightDetailState(instanceDetails);
  applicationStateProvider.addInsightDetailState(getMultipleInstancesState());
});

registerRootState((stateConfigProvider) => stateConfigProvider.addToRootState(getStandaloneInstanceState()));

import type { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import type { ApplicationStateProvider } from '../application/application.state.provider';
import { registerApplicationState } from '../application/applicationState.registration';
import { ClusterMaster } from '../cluster/ClusterMaster';
import { filterModelConfig } from '../cluster/filter/ClusterFilterModel';
import { ClusterFilters } from '../cluster/filter/ClusterFilters';
import { MultipleServerGroupsDetails } from './details/MultipleServerGroupsDetails';
import { ServerGroupDetailsWrapper } from './details/ServerGroupDetailsWrapper';
import type { INestedState, StateConfigProvider } from '../navigation/state.provider';

export const SERVER_GROUP_STATES = 'spinnaker.core.serverGroup.states';

export function getClustersState(stateConfigProvider: StateConfigProvider): INestedState {
  return {
    name: 'clusters',
    url: `/clusters?${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
    views: {
      nav: { component: ClusterFilters, $type: 'react' },
      master: { component: ClusterMaster, $type: 'react' },
    },
    params: stateConfigProvider.buildDynamicParams(filterModelConfig),
    data: {
      pageTitleSection: {
        title: 'Clusters',
      },
    },
    children: [],
  };
}

export function getServerGroupDetailsState(): INestedState {
  return {
    name: 'serverGroup',
    url: '/serverGroupDetails/:provider/:accountId/:region/:serverGroup',
    views: {
      'detail@../insight': { component: ServerGroupDetailsWrapper, $type: 'react' },
    },
    resolve: {
      serverGroup: [
        '$stateParams',
        ($stateParams: StateParams) => {
          return {
            name: $stateParams.serverGroup,
            accountId: $stateParams.accountId,
            provider: $stateParams.provider,
            region: $stateParams.region,
          };
        },
      ],
    },
    data: {
      pageTitleDetails: {
        title: 'Server Group Details',
        nameParam: 'serverGroup',
        accountParam: 'accountId',
        regionParam: 'region',
      },
      history: {
        type: 'serverGroups',
      },
    },
  };
}

export function getMultipleServerGroupsState(): INestedState {
  return {
    name: 'multipleServerGroups',
    url: '/multipleServerGroups',
    views: {
      'detail@../insight': {
        component: MultipleServerGroupsDetails,
        $type: 'react',
      },
    },
    data: {
      pageTitleDetails: {
        title: 'Multiple Server Groups',
      },
    },
  };
}

module(SERVER_GROUP_STATES, []);

registerApplicationState(
  (applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
    const clusters = getClustersState(stateConfigProvider);
    const serverGroupDetails = getServerGroupDetailsState();
    const multipleServerGroups = getMultipleServerGroupsState();

    applicationStateProvider.addInsightState(clusters);
    applicationStateProvider.addInsightDetailState(serverGroupDetails);
    applicationStateProvider.addInsightDetailState(multipleServerGroups);
  },
);

import { module } from 'angular';

import { StateParams } from '@uirouter/angularjs';
import { STATE_CONFIG_PROVIDER, INestedState, StateConfigProvider } from 'core/navigation/state.provider';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from 'core/application/application.state.provider';
import { Application } from 'core/application/application.model';
import { filterModelConfig } from 'core/cluster/filter/ClusterFilterModel';

import { ServerGroupDetailsWrapper } from './details/ServerGroupDetailsWrapper';

export const SERVER_GROUP_STATES = 'spinnaker.core.serverGroup.states';
module(SERVER_GROUP_STATES, [APPLICATION_STATE_PROVIDER, STATE_CONFIG_PROVIDER]).config(
  ['applicationStateProvider', 'stateConfigProvider', (applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
    const clusters: INestedState = {
      name: 'clusters',
      url: `/clusters?${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
      views: {
        nav: {
          template: '<cluster-filter app="$resolve.app"></cluster-filter>',
        },
        master: {
          templateUrl: require('../cluster/allClusters.html'),
          controller: 'AllClustersCtrl',
          controllerAs: 'ctrl',
        },
      },
      redirectTo: transition => {
        return transition
          .injector()
          .getAsync('app')
          .then((app: Application) => {
            if (app.serverGroups.disabled) {
              const relativeSref = app.dataSources.find(ds => ds.sref && !ds.disabled).sref;
              const params = transition.params();
              // Target the state relative to the `clusters` state
              const options = { relative: transition.to().name };
              // Up two state levels first
              return transition.router.stateService.target('^.^' + relativeSref, params, options);
            }
            return null;
          });
      },
      params: stateConfigProvider.buildDynamicParams(filterModelConfig),
      data: {
        pageTitleSection: {
          title: 'Clusters',
        },
      },
      children: [],
    };

    const serverGroupDetails: INestedState = {
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

    const multipleServerGroups: INestedState = {
      name: 'multipleServerGroups',
      url: '/multipleServerGroups',
      views: {
        'detail@../insight': {
          templateUrl: require('../serverGroup/details/multipleServerGroups.view.html'),
          controller: 'MultipleServerGroupsCtrl',
          controllerAs: 'vm',
        },
      },
      data: {
        pageTitleDetails: {
          title: 'Multiple Server Groups',
        },
      },
    };

    applicationStateProvider.addInsightState(clusters);
    applicationStateProvider.addInsightDetailState(serverGroupDetails);
    applicationStateProvider.addInsightDetailState(multipleServerGroups);

    stateConfigProvider.addRewriteRule('/applications/{application}', '/applications/{application}/clusters');
  }],
);

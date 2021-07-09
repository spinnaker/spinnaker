import { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import { Application, APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from '../application';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { CloudProviderRegistry } from '../cloudProvider';
import { InstanceDetails } from './details/InstanceDetails';
import { INestedState, STATE_CONFIG_PROVIDER, StateConfigProvider } from '../navigation';

export const INSTANCE_STATES = 'spinnaker.core.instance.states';
module(INSTANCE_STATES, [APPLICATION_STATE_PROVIDER, STATE_CONFIG_PROVIDER]).config([
  'applicationStateProvider',
  'stateConfigProvider',
  (applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
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

    const multipleInstances: INestedState = {
      name: 'multipleInstances',
      url: '/multipleInstances',
      views: {
        'detail@../insight': {
          templateUrl: require('../instance/details/multipleInstances.view.html'),
          controller: 'MultipleInstancesCtrl',
          controllerAs: 'vm',
        },
      },
      data: {
        pageTitleDetails: {
          title: 'Multiple Instances',
        },
      },
    };

    const standaloneInstance: INestedState = {
      name: 'instanceDetails',
      url: '/instance/:provider/:account/:region/:instanceId',
      views: {
        'main@': {
          templateUrl: require('../presentation/standalone.view.html'),
          controllerProvider: [
            '$stateParams',
            ($stateParams: StateParams) => {
              return CloudProviderRegistry.getValue($stateParams.provider, 'instance.detailsController');
            },
          ],
          controllerAs: 'ctrl',
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

    applicationStateProvider.addInsightDetailState(instanceDetails);
    applicationStateProvider.addInsightDetailState(multipleInstances);
    stateConfigProvider.addToRootState(standaloneInstance);
  },
]);

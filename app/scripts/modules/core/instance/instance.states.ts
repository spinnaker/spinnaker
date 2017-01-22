import {module} from 'angular';

import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider,
  IApplicationStateParams
} from 'core/application/application.state.provider';
import {CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';
import {INestedState, STATE_CONFIG_PROVIDER, StateConfigProvider} from 'core/navigation/state.provider';
import {IStateParamsService} from 'angular-ui-router';
import {Application} from '../application/application.model';
import {ApplicationModelBuilder} from '../application/applicationModel.builder';

export interface IApplicationInstanceDetailsState extends IApplicationStateParams {
  provider: string;
  instanceId: string;
}

export interface IStandaloneInstanceDetailsState extends IStateParamsService {
  provider: string;
  account: string;
  region: string;
  instanceId: string;
}

export const INSTANCE_STATES = 'spinnaker.core.instance.states';
module(INSTANCE_STATES, [
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
]).config((applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {

  const instanceDetails: INestedState = {
    name: 'instanceDetails',
    url: '/instanceDetails/:provider/:instanceId',
    views: {
      'detail@../insight': {
        templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry',
          ($templateCache: ng.ITemplateCacheService,
           $stateParams: IApplicationInstanceDetailsState,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'instance.detailsTemplateUrl'));
        }],
        controllerProvider: ['$stateParams', 'cloudProviderRegistry',
          ($stateParams: IApplicationInstanceDetailsState,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return cloudProviderRegistry.getValue($stateParams.provider, 'instance.detailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      overrides: () => { return {}; },
      instance: ['$stateParams', ($stateParams: IApplicationInstanceDetailsState) => {
        return {
          instanceId: $stateParams.instanceId
        };
      }]
    },
    data: {
      pageTitleDetails: {
        title: 'Instance Details',
        nameParam: 'instanceId'
      },
      history: {
        type: 'instances',
      },
    }
  };

  const multipleInstances: INestedState = {
    name: 'multipleInstances',
    url: '/multipleInstances',
    views: {
      'detail@../insight': {
        templateUrl: require('../instance/details/multipleInstances.view.html'),
        controller: 'MultipleInstancesCtrl',
        controllerAs: 'vm'
      }
    },
    data: {
      pageTitleDetails: {
        title: 'Multiple Instances',
      },
    }
  };

  const standaloneInstance: INestedState = {
    name: 'instanceDetails',
    url: '/instance/:provider/:account/:region/:instanceId',
    views: {
      'main@': {
        templateUrl: require('../presentation/standalone.view.html'),
        controllerProvider: ['$stateParams', 'cloudProviderRegistry',
          ($stateParams: IStandaloneInstanceDetailsState,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return cloudProviderRegistry.getValue($stateParams.provider, 'instance.detailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      instance: ['$stateParams', ($stateParams: IStandaloneInstanceDetailsState) => {
        return {
          instanceId: $stateParams.instanceId,
          account: $stateParams.account,
          region: $stateParams.region,
          noApplication: true
        };
      }],
      app: ['applicationModelBuilder', (applicationModelBuilder: ApplicationModelBuilder): Application => {
        return applicationModelBuilder.createStandaloneApplication('(standalone instance)');
      }],
      overrides: () => { return {}; },
    },
    data: {
      pageTitleDetails: {
        title: 'Instance Details',
        nameParam: 'instanceId'
      },
      history: {
        type: 'instances',
      },
    }
  };

  applicationStateProvider.addInsightDetailState(instanceDetails);
  applicationStateProvider.addInsightDetailState(multipleInstances);
  stateConfigProvider.addToRootState(standaloneInstance);

});

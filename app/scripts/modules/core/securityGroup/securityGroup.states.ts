import {module} from 'angular';

import {INestedState, STATE_CONFIG_PROVIDER, StateConfigProvider} from 'core/navigation/state.provider';
import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider,
  IApplicationStateParams
} from 'core/application/application.state.provider';
import {CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';
import {SecurityGroupReader} from './securityGroupReader.service';
import {IState} from 'angular-ui-router';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from '../application/applicationModel.builder';
import {Application} from '../application/application.model';

export interface IStandaloneSecurityGroupDetailsStateParams extends IState {
  provider: string;
  accountId: string;
  region: string;
  vpcId: string;
  name: string;
}

export interface ISecurityGroupDetailsStateParms extends IStandaloneSecurityGroupDetailsStateParams, IApplicationStateParams {
}

export const SECURITY_GROUP_STATES = 'spinnaker.core.securityGroup.states';
module(SECURITY_GROUP_STATES, [
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
  APPLICATION_MODEL_BUILDER
]).config((applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {

  const securityGroupDetails: INestedState = {
    name: 'securityGroupDetails',
    url: '/securityGroupDetails/:provider/:accountId/:region/:vpcId/:name',
    params: {
      vpcId: {
        value: null,
        squash: true,
      },
    },
    views: {
      'detail@../insight': {
        templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry',
          ($templateCache: ng.ITemplateCacheService,
           $stateParams: ISecurityGroupDetailsStateParms,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'securityGroup.detailsTemplateUrl'));
        }],
        controllerProvider: ['$stateParams', 'cloudProviderRegistry',
          ($stateParams: ISecurityGroupDetailsStateParms,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return cloudProviderRegistry.getValue($stateParams.provider, 'securityGroup.detailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      resolvedSecurityGroup: ['$stateParams', ($stateParams: ISecurityGroupDetailsStateParms) => {
        return {
          name: $stateParams.name,
          accountId: $stateParams.accountId,
          provider: $stateParams.provider,
          region: $stateParams.region,
          vpcId: $stateParams.vpcId,
        };
      }]
    },
    data: {
      pageTitleDetails: {
        title: 'Security Group Details',
        nameParam: 'name',
        accountParam: 'accountId',
        regionParam: 'region'
      },
      history: {
        type: 'securityGroups',
      },
    }
  };

  const securityGroupSummary: INestedState = {
    url: '/securityGroups',
    name: 'securityGroups',
    views: {
      'nav': {
        template: '<security-group-filter app="$resolve.app"></security-group-filter>',
      },
      'master': {
        templateUrl: require('../securityGroup/all.html'),
        controller: 'AllSecurityGroupsCtrl',
        controllerAs: 'ctrl'
      }
    },
    data: {
      pageTitleSection: {
        title: 'Security Groups'
      }
    }
  };

  const standaloneSecurityGroup: INestedState = {
    name: 'securityGroupDetails',
    url: '/securityGroupDetails/:provider/:accountId/:region/:vpcId/:name',
    params: {
      vpcId: {
        value: null,
        squash: true,
      },
    },
    views: {
      'main@': {
        templateUrl: require('../presentation/standalone.view.html'),
        controllerProvider: ['$stateParams', 'cloudProviderRegistry',
          ($stateParams: IStandaloneSecurityGroupDetailsStateParams,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return cloudProviderRegistry.getValue($stateParams.provider, 'securityGroup.detailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      resolvedSecurityGroup: ['$stateParams', ($stateParams: IStandaloneSecurityGroupDetailsStateParams) => {
        return {
          name: $stateParams.name,
          accountId: $stateParams.accountId,
          provider: $stateParams.provider,
          region: $stateParams.region,
          vpcId: $stateParams.vpcId,
        };
      }],
      app: ['$stateParams', 'securityGroupReader', 'applicationModelBuilder',
        ($stateParams: IStandaloneSecurityGroupDetailsStateParams,
         securityGroupReader: SecurityGroupReader,
         applicationModelBuilder: ApplicationModelBuilder): ng.IPromise<Application> => {
          // we need the application to have a security group index (so rules get attached and linked properly)
          // and its name should just be the name of the security group (so cloning works as expected)
          return securityGroupReader.loadSecurityGroups()
            .then((securityGroupsIndex) => {
              const application: Application = applicationModelBuilder.createStandaloneApplication($stateParams.name);
              application['securityGroupsIndex'] = securityGroupsIndex; // TODO: refactor the securityGroupsIndex out
              return application;
            });
      }]
    },
    data: {
      pageTitleDetails: {
        title: 'Security Group Details',
        nameParam: 'name',
        accountParam: 'accountId',
        regionParam: 'region'
      },
      history: {
        type: 'securityGroups',
      },
    }
  };

  applicationStateProvider.addInsightState(securityGroupSummary);
  applicationStateProvider.addInsightDetailState(securityGroupDetails);
  stateConfigProvider.addToRootState(standaloneSecurityGroup);
});

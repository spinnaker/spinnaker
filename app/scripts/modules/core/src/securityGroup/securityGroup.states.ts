import { module } from 'angular';

import { StateParams } from '@uirouter/angularjs';
import { INestedState, STATE_CONFIG_PROVIDER, StateConfigProvider } from 'core/navigation/state.provider';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider, } from 'core/application/application.state.provider';
import { SecurityGroupReader } from './securityGroupReader.service';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';
import { Application } from 'core/application/application.model';
import { filterModelConfig } from './filter/securityGroupFilter.model';
import { VERSIONED_CLOUD_PROVIDER_SERVICE, VersionedCloudProviderService } from 'core/cloudProvider/versionedCloudProvider.service';

export const SECURITY_GROUP_STATES = 'spinnaker.core.securityGroup.states';
module(SECURITY_GROUP_STATES, [
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
  APPLICATION_MODEL_BUILDER,
  VERSIONED_CLOUD_PROVIDER_SERVICE,
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
        templateProvider: ['$templateCache', '$stateParams', 'versionedCloudProviderService',
          ($templateCache: ng.ITemplateCacheService,
           $stateParams: StateParams,
           versionedCloudProviderService: VersionedCloudProviderService) => {
            return versionedCloudProviderService.getValue($stateParams.provider, $stateParams.accountId, 'securityGroup.detailsTemplateUrl')
              .then((templateUrl) => $templateCache.get(templateUrl))
        }],
        controllerProvider: ['$stateParams', 'versionedCloudProviderService',
          ($stateParams: StateParams,
           versionedCloudProviderService: VersionedCloudProviderService) => {
            return versionedCloudProviderService.getValue($stateParams.provider, $stateParams.accountId, 'securityGroup.detailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      resolvedSecurityGroup: ['$stateParams', ($stateParams: StateParams) => {
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
    url: `/securityGroups?${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
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
    params: stateConfigProvider.buildDynamicParams(filterModelConfig),
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
        controllerProvider: ['$stateParams', 'versionedCloudProviderService',
          ($stateParams: StateParams,
           versionedCloudProviderService: VersionedCloudProviderService) => {
            return versionedCloudProviderService.getValue($stateParams.provider, $stateParams.accountId, 'securityGroup.detailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      resolvedSecurityGroup: ['$stateParams', ($stateParams: StateParams) => {
        return {
          name: $stateParams.name,
          accountId: $stateParams.accountId,
          provider: $stateParams.provider,
          region: $stateParams.region,
          vpcId: $stateParams.vpcId,
        };
      }],
      app: ['$stateParams', 'securityGroupReader', 'applicationModelBuilder',
        ($stateParams: StateParams,
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

import { module } from 'angular';
import { StateParams } from '@uirouter/angularjs';

import { INestedState, STATE_CONFIG_PROVIDER, StateConfigProvider } from 'core/navigation';
import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider, Application, APPLICATION_MODEL_BUILDER, ApplicationModelBuilder
} from 'core/application';
import { SKIN_SERVICE, SkinService } from 'core/cloudProvider';

import { SecurityGroupReader } from './securityGroupReader.service';
import { filterModelConfig } from './filter/securityGroupFilter.model';
import { SecurityGroupDetails } from './SecurityGroupDetails'

export const SECURITY_GROUP_STATES = 'spinnaker.core.securityGroup.states';
module(SECURITY_GROUP_STATES, [
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
  APPLICATION_MODEL_BUILDER,
  SKIN_SERVICE,
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
        component: SecurityGroupDetails,
        $type: 'react',
      }
    },
    resolve: {
      accountId: ['$stateParams', ($stateParams: StateParams) => $stateParams.accountId],
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
        controllerProvider: ['$stateParams', 'skinService',
          ($stateParams: StateParams,
           skinService: SkinService) => {
            return skinService.getValue($stateParams.provider, $stateParams.accountId, 'securityGroup.detailsController');
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

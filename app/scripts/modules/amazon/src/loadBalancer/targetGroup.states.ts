import { module } from 'angular';
import { StateParams } from 'angular-ui-router';

import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider, CloudProviderRegistry, INestedState } from '@spinnaker/core';

export const TARGET_GROUP_STATES = 'spinnaker.amazon.loadBalancer.targetGroup.states';
module(TARGET_GROUP_STATES, [
  APPLICATION_STATE_PROVIDER,
]).config((applicationStateProvider: ApplicationStateProvider) => {

  const targetGroupDetails: INestedState = {
    name: 'targetGroupDetails',
    url: '/targetGroupDetails/:provider/:accountId/:region/:vpcId/:loadBalancerName/:name',
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
           $stateParams: StateParams,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'loadBalancer.targetGroupDetailsTemplateUrl'));
        }],
        controllerProvider: ['$stateParams', 'cloudProviderRegistry',
          ($stateParams: StateParams,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return cloudProviderRegistry.getValue($stateParams.provider, 'loadBalancer.targetGroupDetailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      targetGroup: ['$stateParams', ($stateParams: StateParams) => {
        return {
          loadBalancerName: $stateParams.loadBalancerName,
          name: $stateParams.name,
          accountId: $stateParams.accountId,
          region: $stateParams.region,
          vpcId: $stateParams.vpcId
        };
      }]
    },
    data: {
      pageTitleDetails: {
        title: 'Target Group Details',
        nameParam: 'name',
        accountParam: 'accountId',
        regionParam: 'region'
      },
    }
  };

  applicationStateProvider.addInsightDetailState(targetGroupDetails);
});

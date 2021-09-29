import type { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import { SecurityGroupDetails } from './SecurityGroupDetails';
import { SecurityGroups } from './SecurityGroups';
import type { Application, ApplicationStateProvider } from '../application';
import { APPLICATION_STATE_PROVIDER, ApplicationModelBuilder } from '../application';
import { CloudProviderRegistry } from '../cloudProvider';
import { filterModelConfig } from './filter/SecurityGroupFilterModel';
import { SecurityGroupFilters } from './filter/SecurityGroupFilters';
import { FirewallLabels } from './label';
import type { INestedState, StateConfigProvider } from '../navigation';
import { STATE_CONFIG_PROVIDER } from '../navigation';
import type { SecurityGroupReader } from './securityGroupReader.service';

export const SECURITY_GROUP_STATES = 'spinnaker.core.securityGroup.states';
module(SECURITY_GROUP_STATES, [APPLICATION_STATE_PROVIDER, STATE_CONFIG_PROVIDER]).config([
  'applicationStateProvider',
  'stateConfigProvider',
  (applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
    const firewallDetails: INestedState = {
      name: 'firewallDetails',
      url: '/firewallDetails/:provider/:accountId/:region/:vpcId/:name',
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
        },
      },
      resolve: {
        accountId: ['$stateParams', ($stateParams: StateParams) => $stateParams.accountId],
        resolvedSecurityGroup: [
          '$stateParams',
          ($stateParams: StateParams) => {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              provider: $stateParams.provider,
              region: $stateParams.region,
              vpcId: $stateParams.vpcId,
            };
          },
        ],
      },
      data: {
        pageTitleDetails: {
          title: `${FirewallLabels.get('Firewall')} Details`,
          nameParam: 'name',
          accountParam: 'accountId',
          regionParam: 'region',
        },
        history: {
          type: 'securityGroups',
        },
      },
    };

    const securityGroupSummary: INestedState = {
      url: `/firewalls?${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
      name: 'firewalls',
      views: {
        nav: { component: SecurityGroupFilters, $type: 'react' },
        master: {
          component: SecurityGroups,
          $type: 'react',
        },
      },
      params: stateConfigProvider.buildDynamicParams(filterModelConfig),
      data: {
        pageTitleSection: {
          title: FirewallLabels.get('Firewalls'),
        },
      },
    };

    const standaloneFirewall: INestedState = {
      name: 'firewallDetails',
      url: '/firewallDetails/:provider/:accountId/:region/:vpcId/:name',
      params: {
        vpcId: {
          value: null,
          squash: true,
        },
      },
      views: {
        'main@': {
          templateUrl: require('../presentation/standalone.view.html'),
          controllerProvider: [
            '$stateParams',
            ($stateParams: StateParams) => {
              return CloudProviderRegistry.getValue($stateParams.provider, 'securityGroup.detailsController');
            },
          ],
          controllerAs: 'ctrl',
        },
      },
      resolve: {
        resolvedSecurityGroup: [
          '$stateParams',
          ($stateParams: StateParams) => {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              provider: $stateParams.provider,
              region: $stateParams.region,
              vpcId: $stateParams.vpcId,
            };
          },
        ],
        app: [
          '$stateParams',
          'securityGroupReader',
          ($stateParams: StateParams, securityGroupReader: SecurityGroupReader): PromiseLike<Application> => {
            // we need the application to have a firewall index (so rules get attached and linked properly)
            // and its name should just be the name of the firewall (so cloning works as expected)
            return securityGroupReader.loadSecurityGroups().then((securityGroupsIndex) => {
              const application: Application = ApplicationModelBuilder.createStandaloneApplication($stateParams.name);
              application['securityGroupsIndex'] = securityGroupsIndex; // TODO: refactor the securityGroupsIndex out
              return application;
            });
          },
        ],
      },
      data: {
        pageTitleDetails: {
          title: `${FirewallLabels.get('Firewall')} Details`,
          nameParam: 'name',
          accountParam: 'accountId',
          regionParam: 'region',
        },
        history: {
          type: 'securityGroups',
        },
      },
    };

    applicationStateProvider.addInsightState(securityGroupSummary);
    applicationStateProvider.addInsightDetailState(firewallDetails);
    stateConfigProvider.addToRootState(standaloneFirewall);
    stateConfigProvider.addRewriteRule(
      '/applications/{application}/securityGroups',
      '/applications/{application}/firewalls',
    );
    stateConfigProvider.addRewriteRule(/(.+?)\/securityGroupDetails\/(.*)/, ($match: string[]) => {
      return `${$match[1]}/firewallDetails/${$match[2]}`;
    });
  },
]);

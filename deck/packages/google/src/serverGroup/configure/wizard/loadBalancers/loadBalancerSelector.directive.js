'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_ELSEVENOPTIONSGENERATOR_COMPONENT } from './elSevenOptions/elSevenOptionsGenerator.component';
import { GCE_HTTP_LOAD_BALANCER_UTILS } from '../../../../loadBalancer/httpLoadBalancerUtils.service';
import { GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE } from '../../serverGroupConfiguration.service';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_LOADBALANCERSELECTOR_DIRECTIVE =
  'spinnaker.google.serverGroup.configure.wizard.loadBalancers.selector.directive';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_LOADBALANCERSELECTOR_DIRECTIVE; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_LOADBALANCERSELECTOR_DIRECTIVE, [
    GCE_HTTP_LOAD_BALANCER_UTILS,
    GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_ELSEVENOPTIONSGENERATOR_COMPONENT,
    GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE,
  ])
  .directive('gceServerGroupLoadBalancerSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./loadBalancerSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'gceServerGroupLoadBalancerSelectorCtrl',
    };
  })
  .controller('gceServerGroupLoadBalancerSelectorCtrl', [
    'gceHttpLoadBalancerUtils',
    function (gceHttpLoadBalancerUtils) {
      this.showLoadBalancingPolicy = () => {
        if (_.has(this, 'command.backingData.filtered.loadBalancerIndex')) {
          const index = this.command.backingData.filtered.loadBalancerIndex;
          const selected = this.command.loadBalancers;

          return (
            angular.isDefined(selected) &&
            _.some(selected, (s) => {
              return (
                index[s].loadBalancerType === 'HTTP' ||
                index[s].loadBalancerType === 'INTERNAL_MANAGED' ||
                index[s].loadBalancerType === 'SSL' ||
                index[s].loadBalancerType === 'TCP'
              );
            })
          );
        }
      };

      this.isHttpLoadBalancer = (lb) => gceHttpLoadBalancerUtils.isHttpLoadBalancer(lb);
    },
  ]);

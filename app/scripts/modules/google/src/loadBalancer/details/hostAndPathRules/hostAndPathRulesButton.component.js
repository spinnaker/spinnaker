import { module } from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

import { GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_CONTROLLER } from './hostAndPathRules.controller';

('use strict');

export const GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULESBUTTON_COMPONENT =
  'spinnaker.deck.gce.loadBalancer.hostAndPathRulesButton.component';
export const name = GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULESBUTTON_COMPONENT; // for backwards compatibility
module(GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULESBUTTON_COMPONENT, [
  ANGULAR_UI_BOOTSTRAP,
  GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_CONTROLLER,
]).component('gceHostAndPathRulesButton', {
  bindings: {
    hostRules: '=',
    defaultService: '=',
    loadBalancerName: '=',
  },
  template: '<a href ng-click="$ctrl.viewHostAndPathRules()">View Host and Path Rules</a>',
  controller: [
    '$uibModal',
    function ($uibModal) {
      this.viewHostAndPathRules = () => {
        $uibModal.open({
          templateUrl: require('./hostAndPathRules.modal.html'),
          controller: 'gceHostAndPathRulesCtrl',
          controllerAs: 'ctrl',
          size: 'lg',
          resolve: {
            hostRules: () => this.hostRules,
            defaultService: () => this.defaultService,
            loadBalancerName: () => this.loadBalancerName,
          },
        });
      };
    },
  ],
});

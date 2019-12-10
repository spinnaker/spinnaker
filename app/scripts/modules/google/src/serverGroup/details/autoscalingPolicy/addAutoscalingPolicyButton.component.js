'use strict';

const angular = require('angular');

export const GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_ADDAUTOSCALINGPOLICYBUTTON_COMPONENT =
  'spinnaker.gce.serverGroup.details.scalingPolicy.addButton';
export const name = GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_ADDAUTOSCALINGPOLICYBUTTON_COMPONENT; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_ADDAUTOSCALINGPOLICYBUTTON_COMPONENT, [
    require('angular-ui-bootstrap'),
    require('./modal/upsertAutoscalingPolicy.modal.controller').name,
  ])
  .component('gceAddAutoscalingPolicyButton', {
    bindings: {
      serverGroup: '=',
      application: '=',
    },
    template: '<a href ng-click="$ctrl.addAutoscalingPolicy()">Create new scaling policy</a>',
    controller: [
      '$uibModal',
      function($uibModal) {
        this.addAutoscalingPolicy = () => {
          $uibModal.open({
            templateUrl: require('./modal/upsertAutoscalingPolicy.modal.html'),
            controller: 'gceUpsertAutoscalingPolicyModalCtrl',
            controllerAs: 'ctrl',
            size: 'lg',
            resolve: {
              policy: () => undefined,
              serverGroup: () => this.serverGroup,
              application: () => this.application,
            },
          });
        };
      },
    ],
  });

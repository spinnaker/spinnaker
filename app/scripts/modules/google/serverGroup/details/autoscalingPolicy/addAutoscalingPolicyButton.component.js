'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.gce.serverGroup.details.scalingPolicy.addButton', [
    require('angular-ui-bootstrap'),
    require('./modal/upsertAutoscalingPolicy.modal.controller.js'),
  ])
  .component('gceAddAutoscalingPolicyButton', {
    bindings: {
      serverGroup: '=',
      application: '=',
    },
    template: '<a href ng-click="$ctrl.addAutoscalingPolicy()">Create new scaling policy</a>',
    controller: function($uibModal) {

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
          }
        });
      };
    }
  });

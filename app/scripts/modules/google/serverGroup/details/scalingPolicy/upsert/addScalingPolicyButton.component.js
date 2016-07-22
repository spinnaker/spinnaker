'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.gce.serverGroup.details.scalingPolicy.addButton', [
    require('angular-ui-bootstrap'),
    require('./upsertScalingPolicy.controller.js')
  ])
  .component('gceAddScalingPolicyButton', {
    bindings: {
      serverGroup: '=',
      application: '=',
    },
    template: '<a href ng-click="$ctrl.addScalingPolicy()">Create new scaling policy</a>',
    controller: function($uibModal) {

      this.addScalingPolicy = () => {
        $uibModal.open({
          templateUrl: require('./upsertScalingPolicy.modal.html'),
          controller: 'gceUpsertScalingPolicyCtrl',
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

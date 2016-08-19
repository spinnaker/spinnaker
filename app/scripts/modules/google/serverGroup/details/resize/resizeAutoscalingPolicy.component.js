'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.serverGroup.details.resizeAutoscalingPolicy.component', [
    require('../../../autoscalingPolicy/autoscalingPolicy.write.service.js'),
    require('../../../../core/utils/lodash.js'),
  ])
  .component('gceResizeAutoscalingPolicy', {
    bindings: {
      serverGroup: '=',
      command: '=',
      formMethods: '=',
      application: '='
    },
    templateUrl: require('./resizeAutoscalingPolicy.component.html'),
    controller: function ($scope, _, gceAutoscalingPolicyWriter) {
      let newPolicyBounds = ['newMinNumReplicas','newMaxNumReplicas'];
      newPolicyBounds.forEach((prop) => this.command[prop] = null);

      angular.extend(this.formMethods, {
        formIsValid: () => _.every([
          _(newPolicyBounds).map(bound => this.command[bound] !== null).every().valueOf(),
          $scope.resizeAutoscalingPolicyForm.$valid
        ]),
        submitMethod: () => {
          return gceAutoscalingPolicyWriter.upsertAutoscalingPolicy(this.application, this.serverGroup, {
            minNumReplicas: this.command.newMinNumReplicas,
            maxNumReplicas: this.command.newMaxNumReplicas
          }, {
            reason: this.command.reason,
            interestingHealthProviderNames: this.command.interestingHealthProviderNames
          });
        }
      });
    }
  });

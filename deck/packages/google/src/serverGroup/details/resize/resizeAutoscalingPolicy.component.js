'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE } from '../../../autoscalingPolicy/autoscalingPolicy.write.service';

export const GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZEAUTOSCALINGPOLICY_COMPONENT =
  'spinnaker.deck.gce.serverGroup.details.resizeAutoscalingPolicy.component';
export const name = GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZEAUTOSCALINGPOLICY_COMPONENT; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZEAUTOSCALINGPOLICY_COMPONENT, [
    GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE,
  ])
  .component('gceResizeAutoscalingPolicy', {
    bindings: {
      serverGroup: '=',
      command: '=',
      formMethods: '=',
      application: '=',
    },
    templateUrl: require('./resizeAutoscalingPolicy.component.html'),
    controller: [
      '$scope',
      'gceAutoscalingPolicyWriter',
      function ($scope, gceAutoscalingPolicyWriter) {
        // In Angular 1.7 Directive bindings were removed in the constructor, default values now must be instantiated within $onInit
        // See https://docs.angularjs.org/guide/migration#-compile- and https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
        this.$onInit = () => {
          const newPolicyBounds = ['newMinNumReplicas', 'newMaxNumReplicas'];
          newPolicyBounds.forEach((prop) => (this.command[prop] = null));

          angular.extend(this.formMethods, {
            formIsValid: () =>
              _.every([
                _.chain(newPolicyBounds)
                  .map((bound) => this.command[bound] !== null)
                  .every()
                  .value(),
                $scope.resizeAutoscalingPolicyForm.$valid,
              ]),
            submitMethod: () => {
              return gceAutoscalingPolicyWriter.upsertAutoscalingPolicy(
                this.application,
                this.serverGroup,
                {
                  minNumReplicas: this.command.newMinNumReplicas,
                  maxNumReplicas: this.command.newMaxNumReplicas,
                },
                {
                  reason: this.command.reason,
                  interestingHealthProviderNames: this.command.interestingHealthProviderNames,
                },
              );
            },
          });
        };
      },
    ],
  });

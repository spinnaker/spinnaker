'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { SERVER_GROUP_WRITER } from '@spinnaker/core';

export const GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT =
  'spinnaker.google.serverGroup.details.resize.capacity.component';
export const name = GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT, [SERVER_GROUP_WRITER])
  .component('gceResizeCapacity', {
    bindings: {
      command: '=',
      application: '=',
      serverGroup: '=',
      formMethods: '=',
    },
    templateUrl: require('./resizeCapacity.component.html'),
    controller: [
      '$scope',
      'serverGroupWriter',
      function ($scope, serverGroupWriter) {
        // In Angular 1.7 Directive bindings were removed in the constructor, default values now must be instantiated within $onInit
        // See https://docs.angularjs.org/guide/migration#-compile- and https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
        this.$onInit = () => {
          this.command.newSize = null;

          angular.extend(this.formMethods, {
            formIsValid: () => _.every([this.command.newSize !== null, $scope.resizeCapacityForm.$valid]),
            submitMethod: () => {
              return serverGroupWriter.resizeServerGroup(this.serverGroup, this.application, {
                capacity: { min: this.command.newSize, max: this.command.newSize, desired: this.command.newSize },
                serverGroupName: this.serverGroup.name,
                targetSize: this.command.newSize,
                region: this.serverGroup.region,
                interestingHealthProviderNames: this.command.interestingHealthProviderNames,
                reason: this.command.reason,
              });
            },
          });
        };
      },
    ],
  });

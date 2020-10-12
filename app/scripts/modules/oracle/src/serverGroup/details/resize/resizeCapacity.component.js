'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { SERVER_GROUP_WRITER } from '@spinnaker/core';

export const ORACLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT =
  'spinnaker.oracle.serverGroup.details.resize.capacity.component';
export const name = ORACLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT; // for backwards compatibility
angular
  .module(ORACLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT, [SERVER_GROUP_WRITER])
  .component('oracleResizeCapacity', {
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
      },
    ],
  });

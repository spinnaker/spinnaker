'use strict';

const angular = require('angular');
import _ from 'lodash';

import { SERVER_GROUP_WRITER } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.oracle.serverGroup.details.resize.capacity.component', [SERVER_GROUP_WRITER])
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
      function($scope, serverGroupWriter) {
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

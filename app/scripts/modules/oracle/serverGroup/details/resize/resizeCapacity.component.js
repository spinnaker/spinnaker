'use strict';

import _ from 'lodash';
let angular = require('angular');

import {SERVER_GROUP_WRITER} from 'core/serverGroup/serverGroupWriter.service';

module.exports = angular
  .module('spinnaker.oraclebmcs.serverGroup.details.resize.capacity.component', [
    SERVER_GROUP_WRITER,
  ])
  .component('oracleBmcsResizeCapacity', {
    bindings: {
      command: '=',
      application: '=',
      serverGroup: '=',
      formMethods: '='
    },
    templateUrl: require('./resizeCapacity.component.html'),
    controller: function ($scope, serverGroupWriter) {
      this.command.newSize = null;

      angular.extend(this.formMethods, {
        formIsValid: () => _.every([ this.command.newSize !== null, $scope.resizeCapacityForm.$valid ]),
        submitMethod: () => {
          return serverGroupWriter.resizeServerGroup(this.serverGroup, this.application, {
            capacity: { min: this.command.newSize, max: this.command.newSize, desired: this.command.newSize },
            serverGroupName: this.serverGroup.name,
            targetSize: this.command.newSize,
            region: this.serverGroup.region,
            interestingHealthProviderNames: this.command.interestingHealthProviderNames,
            reason: this.command.reason,
          });
        }
      });
    }
  });

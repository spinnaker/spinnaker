'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.details.resize.capacity.component', [
    require('../../../../core/serverGroup/serverGroup.write.service.js'),
    require('../../../../core/utils/lodash.js')
  ])
  .component('gceResizeCapacity', {
    bindings: {
      command: '=',
      application: '=',
      serverGroup: '=',
      formMethods: '='
    },
    templateUrl: require('./resizeCapacity.component.html'),
    controller: function ($scope, serverGroupWriter, _) {
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

'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.pipeline.create.persistedPropertyList.component', [
    require('core/authentication/authentication.service')
  ])
  .component('persistedPropertyCreateList', {
    bindings: {
      stage: '=',
      propertyList: '='
    },
    templateUrl: require('./persistedPropertyList.component.html'),
    controller: 'PropertyListCreateController',
    controllerAs: 'propertyList',
  })
  .controller('PropertyListCreateController', function(authenticationService) {
    let vm = this;

    const user = authenticationService.getAuthenticatedUser();
    vm.addProperty = function () {
      if (!vm.stage.persistedProperties) {
        vm.stage.persistedProperties = [];
      }
      var newProperty = {
        updatedBy: user.name,
        sourceOfUpdate: 'spinnaker'
      };
      vm.stage.persistedProperties.push(newProperty);
    };

  });

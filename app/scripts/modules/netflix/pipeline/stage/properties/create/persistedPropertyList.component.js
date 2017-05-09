'use strict';

import {AUTHENTICATION_SERVICE} from 'core/authentication/authentication.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.pipeline.create.persistedPropertyList.component', [AUTHENTICATION_SERVICE])
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
        sourceOfUpdate: 'spinnaker',
        ttl: 0,
      };
      vm.stage.persistedProperties.push(newProperty);
    };

  });

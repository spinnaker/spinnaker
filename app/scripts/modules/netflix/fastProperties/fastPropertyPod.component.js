'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperty.pod.component', [
    require('angular-ui-router'),
  ])
  .component('fastPropertyPod', {
    templateUrl: require('./fastPropertyPod.html'),
    bindings: {
      key: '=',
      values: '='
    },
    controller: function($state) {
      let vm = this;

      vm.$state = $state;

      vm.showPropertyDetails = (propertyId) => {
        if ($state.current.name.includes('.properties.propertyDetails')) {
          $state.go('^.propertyDetails', {propertyId: propertyId}, {inherit: true});
        } else {
          $state.go('.propertyDetails', {propertyId: propertyId}, {inherit: true});
        }

      };
    },
    controllerAs: 'fpPod'
  });

'use strict';

let angular = require('angular');
import _ from 'lodash';

module.exports = angular
  .module('spinnaker.netfilx.globalFastProperty.pods.component', [
    require('angular-ui-router'),
  ])
  .component('fastPropertyPods', {
    templateUrl: require('./fastPropertyPods.html'),
    bindings: {
      properties: '=',
      groupedBy: '=?'
    },
    controller: function($state, $stateParams) {
      let vm = this;
      vm.$state = $state;

      vm.isArray = () => angular.isArray(vm.properties);
      vm.isGrouped = () => !vm.isArray();

      let hasPropId = (prop) => prop.propertyId === $stateParams.propertyId;

      let isDetailInPropInList = () => {
        let included = false;

        if(vm.isArray()) {
          included = vm.properties.some(hasPropId);
        } else {
          included = _.flatten(_.values(vm.properties)).some(hasPropId);
        }
        return included;
      };

      let resetPropertyDetails = () => {
        if($stateParams.propertyId && !isDetailInPropInList()) {
          delete $stateParams.propertyId;
          $state.go('home.data.properties', $stateParams);
        }
      };

      resetPropertyDetails();
    },
    controllerAs: 'fpPod'
  })
  .component('fastPropertyPodTable', {
    templateUrl: require('./fastPropertyPodTable.html'),
    bindings: {
      properties: '=',
      groupedBy: '=?'
    },
    controller: function($state) {
      let vm = this;

      vm.getBaseOfScope = (scope) => {
        if (scope.serverId) { return scope.serverId; }
        if (scope.zone) { return scope.zone; }
        if (scope.asg) { return scope.asg; }
        if (scope.cluster) { return scope.cluster; }
        if (scope.stack) { return scope.stack; }
        if (scope.region) { return scope.region; }
        if (scope.appId) { return scope.appId; }
        return 'GLOBAL';
      };

      vm.showPropertyDetails = (propertyId) => {
        if ($state.current.name.includes('.data.properties')) {
          if ($state.current.name.includes('.data.properties.globalFastPropertyDetails')) {
            $state.go('^.globalFastPropertyDetails', {propertyId: propertyId}, {inherit: true});
          } else {
            $state.go('.globalFastPropertyDetails', {propertyId: propertyId}, {inherit: true});
          }
        }

        if ($state.current.name.includes('.application.propInsights')) {
          if ($state.current.name.includes('.application.propInsights.properties.propertyDetails')) {
            $state.go('^.propertyDetails', {propertyId: propertyId}, {inherit: true});
          } else {
            $state.go('.propertyDetails', {propertyId: propertyId}, {inherit: true});
          }
        }

      };

    },

  });

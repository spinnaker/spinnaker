'use strict';

let angular = require('angular');
import _ from 'lodash';

module.exports = angular
  .module('spinnaker.netfilx.globalFastProperty.pods.component', [
    require('angular-ui-router'),
  ])
  .component('globalFastPropertyPods', {
    templateUrl: require('./globalFastPropertyPods.html'),
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
  .component('globalFastPropertyPodTable', {
    templateUrl: require('./globalFastPropertyPodTable.html'),
    bindings: {
      properties: '=',
      groupedBy: '=?'
    },
    controller: function($state) {
      let vm = this;
      vm.showPropertyDetails = (propertyId) => {
        if ($state.current.name.includes('.properties.globalFastPropertyDetails')) {
          $state.go('^.globalFastPropertyDetails', {propertyId: propertyId}, {inherit: true});
        } else {
          $state.go('.globalFastPropertyDetails', {propertyId: propertyId}, {inherit: true});
        }
      };

    },

  });

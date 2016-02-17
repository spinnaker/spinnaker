'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.accountZoneClusterSelector.directive', [])
  .directive('accountZoneClusterSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        application: '=',
        component: '=',
        accounts: '=',
      },
      templateUrl: require('./accountZoneClusterSelector.component.html'),
      controllerAs: 'vm',
      controller: function controller(appListExtractorService, accountService) {
        let vm = this;
        let isTextInputForClusterFiled;

        let zones = {'us-central1': ['us-central1-a', 'us-central1-b', 'us-central1-c']};

        let setZoneList = () => {
          accountService.getRegionsForAccount(vm.component.credentials).then(function(zoneMap) {
            vm.zones = zoneMap ? zoneMap : zones;
          });
        };

        let setClusterList = () => {
          let clusterFilter = appListExtractorService.clusterFilterForCredentialsAndZone(vm.component.credentials, vm.component.zones);
          vm.clusterList = appListExtractorService.getClusters([vm.application], clusterFilter);
        };

        vm.zoneChanged = () => {
          setClusterList();
          if (!isTextInputForClusterFiled && !vm.clusterList.includes(vm.component.cluster)) {
            vm.component.cluster = undefined;
          }
        };

        let setToggledState = () => {
          vm.zones = zones;
          isTextInputForClusterFiled = true;
        };

        let setUnToggledState = () => {
          vm.component.cluster = undefined;
          isTextInputForClusterFiled = false;
          setZoneList();
        };

        vm.clusterSelectInputToggled = (isToggled) => {
          isToggled ? setToggledState() : setUnToggledState();
        };

        vm.accountUpdated = function() {
          vm.component.cluster = undefined;
          setZoneList();
          setClusterList();
        };

        let init = () => {
          setZoneList();
          setClusterList();
          vm.zones = vm.clusterList.includes(vm.component.cluster) ? vm.zones : zones;
        };

        init();
      }
    };
  });

'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.accountZoneClusterSelector.directive', [
    require('../../core/application/listExtractor/listExtractor.service'),
    require('../../core/account/account.service'),
    require('../../core/utils/lodash'),
  ])
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
      controller: function controller(appListExtractorService, accountService, _) {
        let vm = this;
        let isTextInputForClusterFiled;

        let zones;

        let setZoneList = () => {
          let accountFilter = (cluster) => cluster.account === vm.component.credentials;
          let zoneList = appListExtractorService.getZonesByRegion([vm.application], accountFilter);
          vm.zones = Object.keys(zoneList).length ? zoneList : zones;

          // Deselect any zones that are not included in the filtered list.
          let flattenedZoneList = _(vm.zones)
            .map()
            .flatten()
            .value();
          vm.component.zones = _.filter(vm.component.zones, zone => {
            return _.includes(flattenedZoneList, zone);
          });
        };

        let setClusterList = () => {
          let clusterFilter = appListExtractorService.clusterFilterForCredentialsAndZone(vm.component.credentials, vm.component.zones);
          vm.clusterList = appListExtractorService.getClusters([vm.application], clusterFilter);
        };

        vm.zoneChanged = () => {
          setClusterList();
          if (!isTextInputForClusterFiled && _.includes(!vm.clusterList, vm.component.cluster)) {
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
          accountService.getUniqueGceZonesForAllAccounts(vm.component.cloudProviderType).then((allZones) => {
            zones = allZones;
            return allZones;
          })
          .then((allZones) => {
            setZoneList();
            setClusterList();
            vm.zones = _.includes(vm.clusterList, vm.component.cluster) ? vm.zones : allZones;
          });
        };

        init();
      }
    };
  });

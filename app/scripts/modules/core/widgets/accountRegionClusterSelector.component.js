'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.accountRegionClusterSelector.directive', [
    require('../../core/application/listExtractor/listExtractor.service'),
  ])
  .directive('accountRegionClusterSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        application: '=',
        component: '=',
        accounts: '=',
      },
      templateUrl: require('./accountRegionClusterSelector.component.html'),
      controllerAs: 'vm',
      controller: function controller(appListExtractorService) {
        let vm = this;
        let isTextInputForClusterFiled;

        let regions = ['us-east-1', 'us-west-1', 'eu-west-1', 'us-west-2'];

        let setRegionList = () => {
          let accountFilter = (cluster) => cluster.account === vm.component.credentials;
          let regionList = appListExtractorService.getRegions([vm.application], accountFilter);
          vm.regions = regionList.length ? regionList : regions;
        };


        let setClusterList = () => {
          let clusterFilter = appListExtractorService.clusterFilterForCredentialsAndRegion(vm.component.credentials, vm.component.regions);
          vm.clusterList = appListExtractorService.getClusters([vm.application], clusterFilter);
        };

        vm.accountChange = () => {
          vm.accountUpdated();
        };

        vm.regionChanged = () => {
          setClusterList();
          if (!isTextInputForClusterFiled && !vm.clusterList.includes(vm.component.cluster)) {
            vm.component.cluster = undefined;
          }
        };

        let setToggledState = () => {
          vm.regions = regions;
          isTextInputForClusterFiled = true;
        };

        let setUnToggledState = () => {
          vm.component.cluster = undefined;
          isTextInputForClusterFiled = false;
          setRegionList();
        };

        vm.clusterSelectInputToggled = (isToggled) => {
          isToggled ? setToggledState() : setUnToggledState();
        };

        vm.accountUpdated = function() {
          vm.component.cluster = undefined;
          setRegionList();
          setClusterList();
        };

        let init = () => {
          setRegionList();
          setClusterList();
          vm.regions = vm.clusterList.includes(vm.component.cluster) ? vm.regions : regions;
        };

        init();
      }
    };
  });

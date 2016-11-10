'use strict';

import _ from 'lodash';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular
  .module('spinnaker.core.accountRegionClusterSelector.directive', [
    require('core/application/listExtractor/listExtractor.service'),
    ACCOUNT_SERVICE,
  ])
  .directive('accountRegionClusterSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        application: '=',
        component: '=',
        accounts: '=',
        clusterField: '@',
        singleRegion: '=',
        showAllRegions: '=?'
      },
      templateUrl: require('./accountRegionClusterSelector.component.html'),
      controllerAs: 'vm',
      controller: function controller(appListExtractorService, accountService) {

        this.clusterField = this.clusterField || 'cluster';

        let vm = this;
        let showAllRegions = vm.showAllRegions || false;

        let isTextInputForClusterFiled;

        let regions;

        let setRegionList = () => {
          let accountFilter = (cluster) => {
            return cluster ? cluster.account === vm.component.credentials : true;
          };
          let regionList = appListExtractorService.getRegions([vm.application], accountFilter);
          vm.regions = showAllRegions ? regions : regionList.length ? regionList : regions;
        };


        let setClusterList = () => {
          let regionField = this.singleRegion ? vm.component.region : vm.component.regions;
          let clusterFilter = appListExtractorService.clusterFilterForCredentialsAndRegion(vm.component.credentials, regionField);
          vm.clusterList = appListExtractorService.getClusters([vm.application], clusterFilter);
        };

        vm.regionChanged = () => {
          setClusterList();
          if (!isTextInputForClusterFiled && ! _.includes(vm.clusterList, vm.component[this.clusterField])) {
            vm.component[this.clusterField] = undefined;
          }
        };

        let setToggledState = () => {
          vm.regions = regions;
          isTextInputForClusterFiled = true;
        };

        let setUnToggledState = () => {
          vm.component[this.clusterField] = undefined;
          isTextInputForClusterFiled = false;
          setRegionList();
        };

        vm.clusterSelectInputToggled = (isToggled) => {
          isToggled ? setToggledState() : setUnToggledState();
        };

        vm.accountUpdated = () => {
          vm.component[this.clusterField] = undefined;
          setRegionList();
          setClusterList();
        };

        let init = () => {
          accountService.getUniqueAttributeForAllAccounts(vm.component.cloudProviderType, 'regions').then((allRegions) => {
            regions = allRegions;

            // TODO(duftler): Remove this once we finish deprecating the old style regions/zones in clouddriver GCE credentials.
            let regionObjs = _.filter(regions, region => _.isObject(region));
            if (regionObjs.length) {
              let oldStyleRegions = _.chain(regionObjs)
                .map(regionObj => _.keys(regionObj))
                .flatten()
                .value();
              regions = _.chain(regions)
                .difference(regionObjs)
                .union(oldStyleRegions)
                .value();
            }
            return regions;
          })
          .then((allRegions) => {
            setRegionList();
            setClusterList();
            vm.regions = _.includes(vm.clusterList, vm.component[this.clusterField]) ? vm.regions : allRegions;
          });
        };

        init();
      }
    };
  });

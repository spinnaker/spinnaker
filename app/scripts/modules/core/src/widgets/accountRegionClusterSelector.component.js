'use strict';

import _ from 'lodash';

const angular = require('angular');

import { AccountService } from 'core/account/AccountService';
import { AppListExtractor } from 'core/application/listExtractor/AppListExtractor';

module.exports = angular
  .module('spinnaker.core.accountRegionClusterSelector.directive', [])
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
        showAllRegions: '=?',
        onAccountUpdate: '&?',
        disableRegionSelect: '=?',
        showClusterSelect: '=?',
      },
      templateUrl: require('./accountRegionClusterSelector.component.html'),
      controllerAs: 'vm',
      controller: function controller() {
        this.clusterField = this.clusterField || 'cluster';

        let vm = this;

        if (vm.showClusterSelect === undefined) {
          vm.showClusterSelect = true;
        }

        let showAllRegions = vm.showAllRegions || false;

        let isTextInputForClusterFiled;

        let regions;

        let setRegionList = () => {
          let accountFilter = cluster => (cluster ? cluster.account === vm.component.credentials : true);
          let regionList = AppListExtractor.getRegions([vm.application], accountFilter);
          vm.regions = showAllRegions ? regions : regionList.length ? regionList : regions;
          (vm.regions || []).sort();
        };

        let setClusterList = () => {
          let regionField = this.singleRegion ? vm.component.region : vm.component.regions;
          let clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(
            vm.component.credentials,
            regionField,
          );
          vm.clusterList = AppListExtractor.getClusters([vm.application], clusterFilter);
        };

        vm.regionChanged = () => {
          setClusterList();
          if (!isTextInputForClusterFiled && !_.includes(vm.clusterList, vm.component[this.clusterField])) {
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

        vm.clusterSelectInputToggled = isToggled => {
          isToggled ? setToggledState() : setUnToggledState();
        };

        vm.clusterChanged = clusterName => {
          const filterByCluster = AppListExtractor.monikerClusterNameFilter(clusterName);
          let clusterMoniker = _.first(_.uniq(AppListExtractor.getMonikers([vm.application], filterByCluster)));
          if (_.isNil(clusterMoniker)) {
            //remove the moniker from the stage if one doesn't exist.
            vm.component.moniker = undefined;
          } else {
            //clusters don't contain sequences, so null it out.
            clusterMoniker.sequence = null;
            vm.component.moniker = clusterMoniker;
          }
        };

        vm.accountUpdated = () => {
          vm.component[this.clusterField] = undefined;
          setRegionList();
          setClusterList();
          if (vm.onAccountUpdate) {
            vm.onAccountUpdate();
          }
        };

        let init = () => {
          AccountService.getUniqueAttributeForAllAccounts(vm.component.cloudProviderType, 'regions')
            .then(allRegions => {
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
              return regions.sort();
            })
            .then(allRegions => {
              setRegionList();
              setClusterList();
              vm.regions = _.includes(vm.clusterList, vm.component[this.clusterField]) ? vm.regions : allRegions;
            });
        };

        init();
      },
    };
  });

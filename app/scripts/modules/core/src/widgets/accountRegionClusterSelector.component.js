'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService } from '../account/AccountService';
import { AppListExtractor } from '../application/listExtractor/AppListExtractor';

export const CORE_WIDGETS_ACCOUNTREGIONCLUSTERSELECTOR_COMPONENT =
  'spinnaker.core.accountRegionClusterSelector.directive';
export const name = CORE_WIDGETS_ACCOUNTREGIONCLUSTERSELECTOR_COMPONENT; // for backwards compatibility
module(CORE_WIDGETS_ACCOUNTREGIONCLUSTERSELECTOR_COMPONENT, []).directive('accountRegionClusterSelector', function () {
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

      const vm = this;

      if (vm.showClusterSelect === undefined) {
        vm.showClusterSelect = true;
      }

      const showAllRegions = vm.showAllRegions || false;

      let isTextInputForClusterFiled;

      let regions;

      const setRegionList = () => {
        const accountFilter = (cluster) => (cluster ? cluster.account === vm.component.credentials : true);
        const regionList = AppListExtractor.getRegions([vm.application], accountFilter);
        vm.regions = showAllRegions ? regions : regionList.length ? regionList : regions;
        (vm.regions || []).sort();
      };

      const setClusterList = () => {
        const regionField = this.singleRegion ? vm.component.region : vm.component.regions;
        const clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(
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

      const setToggledState = () => {
        vm.regions = regions;
        isTextInputForClusterFiled = true;
      };

      const setUnToggledState = () => {
        vm.component[this.clusterField] = undefined;
        isTextInputForClusterFiled = false;
        setRegionList();
      };

      vm.clusterSelectInputToggled = (isToggled) => {
        isToggled ? setToggledState() : setUnToggledState();
      };

      vm.clusterChanged = (clusterName) => {
        const filterByCluster = AppListExtractor.monikerClusterNameFilter(clusterName);
        const clusterMoniker = _.first(_.uniq(AppListExtractor.getMonikers([vm.application], filterByCluster)));
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

      const init = () => {
        AccountService.getUniqueAttributeForAllAccounts(vm.component.cloudProviderType, 'regions')
          .then((allRegions) => {
            regions = allRegions;

            // TODO(duftler): Remove this once we finish deprecating the old style regions/zones in clouddriver GCE credentials.
            const regionObjs = _.filter(regions, (region) => _.isObject(region));
            if (regionObjs.length) {
              const oldStyleRegions = _.chain(regionObjs)
                .map((regionObj) => _.keys(regionObj))
                .flatten()
                .value();
              regions = _.chain(regions).difference(regionObjs).union(oldStyleRegions).value();
            }
            return regions.sort();
          })
          .then((allRegions) => {
            setRegionList();
            setClusterList();
            vm.regions = _.includes(vm.clusterList, vm.component[this.clusterField]) ? vm.regions : allRegions;
          });
      };

      init();
    },
  };
});

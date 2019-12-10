'use strict';

import _ from 'lodash';

const angular = require('angular');

import { AccountService } from 'core/account/AccountService';
import { AppListExtractor } from 'core/application/listExtractor/AppListExtractor';

export const CORE_WIDGETS_ACCOUNTNAMESPACECLUSTERSELECTOR_COMPONENT =
  'spinnaker.core.accountNamespaceClusterSelector.directive';
export const name = CORE_WIDGETS_ACCOUNTNAMESPACECLUSTERSELECTOR_COMPONENT; // for backwards compatibility
angular
  .module(CORE_WIDGETS_ACCOUNTNAMESPACECLUSTERSELECTOR_COMPONENT, [])
  .directive('accountNamespaceClusterSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        application: '=',
        component: '=',
        accounts: '=',
        clusterField: '@',
        provider: '=',
      },
      templateUrl: require('./accountNamespaceClusterSelector.component.html'),
      controllerAs: 'vm',
      controller: function controller() {
        this.clusterField = this.clusterField || 'cluster';

        let vm = this;
        let isTextInputForClusterFiled;

        let namespaces;

        let setNamespaceList = () => {
          let accountFilter = cluster => (cluster ? cluster.account === vm.component.credentials : true);
          // TODO(lwander): Move away from regions to namespaces here.
          let namespaceList = AppListExtractor.getRegions([vm.application], accountFilter);
          vm.namespaces = namespaceList.length ? namespaceList : namespaces;
        };

        let setClusterList = () => {
          let namespaceField = vm.component.namespaces;
          // TODO(lwander): Move away from regions to namespaces here.
          let clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(
            vm.component.credentials,
            namespaceField,
          );
          vm.clusterList = AppListExtractor.getClusters([vm.application], clusterFilter);
        };

        vm.namespaceChanged = () => {
          setClusterList();
          if (!isTextInputForClusterFiled && !_.includes(vm.clusterList, vm.component[this.clusterField])) {
            vm.component[this.clusterField] = undefined;
          }
        };

        let setToggledState = () => {
          vm.namespaces = namespaces;
          isTextInputForClusterFiled = true;
        };

        let setUnToggledState = () => {
          vm.component[this.clusterField] = undefined;
          isTextInputForClusterFiled = false;
          setNamespaceList();
        };

        vm.clusterSelectInputToggled = isToggled => {
          isToggled ? setToggledState() : setUnToggledState();
        };

        vm.accountUpdated = () => {
          vm.component[this.clusterField] = undefined;
          setNamespaceList();
          setClusterList();
        };

        let init = () => {
          AccountService.getUniqueAttributeForAllAccounts(vm.component.cloudProviderType, 'namespaces')
            .then(allNamespaces => {
              namespaces = allNamespaces;
              return allNamespaces;
            })
            .then(allNamespaces => {
              setNamespaceList();
              setClusterList();
              vm.namespaces = _.includes(vm.clusterList, vm.component[this.clusterField])
                ? vm.namespaces
                : allNamespaces;
            });
        };

        init();
      },
    };
  });

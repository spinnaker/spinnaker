'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService, AppListExtractor } from '@spinnaker/core';

export const KUBERNETES_V1_NAMESPACE_MULTISELECTFIELD_COMPONENT = 'kubernetes.namespace.multiSelectField.component';
export const name = KUBERNETES_V1_NAMESPACE_MULTISELECTFIELD_COMPONENT; // for backwards compatibility
module(KUBERNETES_V1_NAMESPACE_MULTISELECTFIELD_COMPONENT, []).directive('namespaceMultiSelectField', function() {
  return {
    restrict: 'E',
    scope: {},
    bindToController: {
      application: '=',
      component: '=',
      accounts: '=',
      clusterField: '@',
    },
    templateUrl: require('./multiSelectField.component.html'),
    controllerAs: 'vm',
    controller: function controller() {
      this.clusterField = this.clusterField || 'cluster';

      const vm = this;
      let isTextInputForClusterFiled;

      let namespaces;

      const setNamespaceList = () => {
        const accountFilter = cluster => (cluster ? cluster.account === vm.component.credentials : true);
        const namespaceList = AppListExtractor.getRegions([vm.application], accountFilter);
        vm.namespaces = namespaceList.length ? namespaceList : namespaces;
      };

      const setClusterList = () => {
        const namespaceField = vm.component.regionss;
        const clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(
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

      const setToggledState = () => {
        vm.namespaces = namespaces;
        isTextInputForClusterFiled = true;
      };

      const setUnToggledState = () => {
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

      const init = () => {
        AccountService.getUniqueRegionsForAllAccounts(vm.component.cloudProviderType)
          .then(allNamespaces => {
            namespaces = allNamespaces;
            return allNamespaces;
          })
          .then(allNamespaces => {
            setNamespaceList();
            setClusterList();
            vm.namespaces = _.includes(vm.clusterList, vm.component[this.clusterField]) ? vm.namespaces : allNamespaces;
          });
      };

      init();
    },
  };
});

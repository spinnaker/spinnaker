'use strict';

import * as angular from 'angular';

export const CORE_WIDGETS_SCOPECLUSTERSELECTOR_DIRECTIVE = 'spinnaker.widget.clusterSelector.directive';
export const name = CORE_WIDGETS_SCOPECLUSTERSELECTOR_DIRECTIVE; // for backwards compatibility
angular.module(CORE_WIDGETS_SCOPECLUSTERSELECTOR_DIRECTIVE, []).directive('clusterSelector', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: {
      model: '=',
      clusters: '=',
      required: '=?',
      toggled: '&?',
      onChange: '&?',
    },
    controllerAs: 'vm',
    controller: function controller() {
      const vm = this;

      vm.toggled = vm.toggled || angular.noop;
      vm.onChange = vm.onChange || angular.noop;
      vm.required = vm.required || false;

      const selectedNotInClusterList = () => {
        return !(
          angular.isArray(vm.clusters) &&
          vm.clusters.length &&
          vm.clusters.some((cluster) => cluster === vm.model)
        );
      };

      const modelIsSet = () => {
        return vm.model !== undefined || vm.model !== null || vm.model.trim() !== '';
      };

      vm.clusterChanged = function () {
        vm.onChange({ clusterName: vm.model });
      };

      vm.freeFormClusterField = modelIsSet() ? selectedNotInClusterList() : false;

      vm.toggleFreeFormClusterField = function (event) {
        event.preventDefault();
        vm.freeFormClusterField = !vm.freeFormClusterField;
        vm.toggled({ isToggled: vm.freeFormClusterField });
      };
    },
    templateUrl: require('./scopeClusterSelector.directive.html'),
  };
});

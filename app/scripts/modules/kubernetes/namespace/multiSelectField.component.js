'use strict';

import _ from 'lodash';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {LIST_EXTRACTOR_SERVICE} from 'core/application/listExtractor/listExtractor.service';

module.exports = angular
  .module('kubernetes.namespace.multiSelectField.component', [
    LIST_EXTRACTOR_SERVICE,
    ACCOUNT_SERVICE,
  ])
  .directive('namespaceMultiSelectField', function() {
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
      controller: function controller(appListExtractorService, accountService) {

        this.clusterField = this.clusterField || 'cluster';

        let vm = this;
        let isTextInputForClusterFiled;

        let namespaces;

        let setNamespaceList = () => {
          let accountFilter = (cluster) => cluster ? cluster.account === vm.component.credentials : true;
          let namespaceList = appListExtractorService.getRegions([vm.application], accountFilter);
          vm.namespaces = namespaceList.length ? namespaceList : namespaces;
        };


        let setClusterList = () => {
          let namespaceField = vm.component.regionss;
          let clusterFilter = appListExtractorService.clusterFilterForCredentialsAndRegion(vm.component.credentials, namespaceField);
          vm.clusterList = appListExtractorService.getClusters([vm.application], clusterFilter);
        };

        vm.namespaceChanged = () => {
          setClusterList();
          if (!isTextInputForClusterFiled && ! _.includes(vm.clusterList, vm.component[this.clusterField])) {
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

        vm.clusterSelectInputToggled = (isToggled) => {
          isToggled ? setToggledState() : setUnToggledState();
        };

        vm.accountUpdated = () => {
          vm.component[this.clusterField] = undefined;
          setNamespaceList();
          setClusterList();
        };

        let init = () => {
          accountService.getUniqueRegionsForAllAccounts(vm.component.cloudProviderType).then((allNamespaces) => {
            namespaces = allNamespaces;
            return allNamespaces;
          })
          .then((allNamespaces) => {
            setNamespaceList();
            setClusterList();
            vm.namespaces = _.includes(vm.clusterList, vm.component[this.clusterField]) ? vm.namespaces : allNamespaces;
          });
        };

        init();
      }
    };
  });

'use strict';

const angular = require('angular');

import {
  AccountService,
  FirewallLabels,
  LoadBalancerWriter,
  SECURITY_GROUP_READER,
  TaskMonitor,
} from '@spinnaker/core';

import '../../loadBalancer.less';

module.exports = angular
  .module('spinnaker.loadBalancer.openstack.create.controller', [
    require('@uirouter/angularjs').default,
    require('../../transformer').name,
    require('../../../region/regionSelectField.directive').name,
    require('../../../subnet/subnetSelectField.directive').name,
    require('../../../network/networkSelectField.directive').name,
    require('../../../common/isolateForm.directive').name,
    SECURITY_GROUP_READER,
  ])
  .controller('openstackUpsertLoadBalancerController', [
    '$scope',
    '$uibModalInstance',
    '$state',
    'application',
    'loadBalancer',
    'isNew',
    'openstackLoadBalancerTransformer',
    'securityGroupReader',
    function(
      $scope,
      $uibModalInstance,
      $state,
      application,
      loadBalancer,
      isNew,
      openstackLoadBalancerTransformer,
      securityGroupReader,
    ) {
      var ctrl = this;
      $scope.isNew = isNew;
      $scope.application = application;

      $scope.pages = {
        location: require('./location.html'),
        interface: require('./interface.html'),
        listeners: require('./listeners.html'),
        healthCheck: require('./healthCheck.html'),
      };

      $scope.state = {
        accountsLoaded: false,
        submitting: false,
      };

      $scope.firewallsLabel = FirewallLabels.get('Firewalls');

      $scope.subnetFilter = {};

      $scope.protocols = ['HTTP', 'HTTPS'];
      $scope.maxPort = 65535;
      $scope.methods = [
        { label: 'Round Robin', value: 'ROUND_ROBIN' },
        { label: 'Least Connections', value: 'LEAST_CONNECTIONS' },
        { label: 'Source IP', value: 'SOURCE_IP' },
      ];

      $scope.allSecurityGroups = [];
      $scope.$watch('loadBalancer.account', updateSecurityGroups);
      $scope.$watch('loadBalancer.region', updateSecurityGroups);
      $scope.updateSecurityGroups = updateSecurityGroups;
      updateSecurityGroups();

      // initialize controller
      if (loadBalancer) {
        $scope.loadBalancer = openstackLoadBalancerTransformer.convertLoadBalancerForEditing(loadBalancer);
      } else {
        $scope.loadBalancer = openstackLoadBalancerTransformer.constructNewLoadBalancerTemplate();
        updateLoadBalancerNames();
      }

      finishInitialization();

      function onApplicationRefresh() {
        // If the user has already closed the modal, do not navigate to the new details view
        if ($scope.$$destroyed) {
          return;
        }
        $uibModalInstance.close();
        var newStateParams = {
          provider: 'openstack',
          name: $scope.loadBalancer.name,
          accountId: $scope.loadBalancer.account,
          region: $scope.loadBalancer.region,
        };
        if (!$state.includes('**.loadBalancerDetails')) {
          $state.go('.loadBalancerDetails', newStateParams);
        } else {
          $state.go('^.loadBalancerDetails', newStateParams);
        }
      }

      function onTaskComplete() {
        application.loadBalancers.refresh();
        application.loadBalancers.onNextRefresh($scope, onApplicationRefresh);
      }

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
        modalInstance: $uibModalInstance,
        onTaskComplete: onTaskComplete,
      });

      function finishInitialization() {
        AccountService.listAccounts('openstack').then(function(accounts) {
          $scope.accounts = accounts;
          $scope.state.accountsLoaded = true;

          var accountNames = _.map($scope.accounts, 'name');
          if (accountNames.length && !accountNames.includes($scope.loadBalancer.account)) {
            $scope.loadBalancer.account = accountNames[0];
          }

          ctrl.accountUpdated();
        });
      }

      function updateLoadBalancerNames() {
        var account = $scope.loadBalancer.credentials || $scope.loadBalancer.account;

        const accountLoadBalancersByRegion = {};
        application
          .getDataSource('loadBalancers')
          .refresh(true)
          .then(() => {
            application.getDataSource('loadBalancers').data.forEach(loadBalancer => {
              if (loadBalancer.account === account) {
                accountLoadBalancersByRegion[loadBalancer.region] =
                  accountLoadBalancersByRegion[loadBalancer.region] || [];
                accountLoadBalancersByRegion[loadBalancer.region].push(loadBalancer.name);
              }
            });

            $scope.existingLoadBalancerNames = _.flatten(_.map(accountLoadBalancersByRegion));
          });
      }

      function updateSecurityGroups() {
        var account = _.get($scope, ['loadBalancer', 'account']);
        var region = _.get($scope, ['loadBalancer', 'region']);
        if (!account || !region) {
          $scope.allSecurityGroups = [];
        }
        securityGroupReader.getAllSecurityGroups().then(function(securityGroups) {
          $scope.allSecurityGroups = _.get(securityGroups, [account, 'openstack', region], []);
        });
      }

      // Controller API
      this.updateName = function() {
        if (!isNew) {
          return;
        }

        var loadBalancer = $scope.loadBalancer;
        var loadBalancerName = [application.name, loadBalancer.stack || '', loadBalancer.detail || ''].join('-');
        loadBalancer.name = _.trimEnd(loadBalancerName, '-');
      };

      this.accountUpdated = function() {
        ctrl.updateName();
        $scope.subnetFilter = {
          type: 'openstack',
          account: $scope.loadBalancer.account,
          region: $scope.loadBalancer.region,
        };
        if ($scope.loadBalancer) {
          $scope.loadBalancer.securityGroups = [];
        }
        updateLoadBalancerNames();
      };

      this.onRegionChanged = function(regionId) {
        $scope.loadBalancer.region = regionId;

        //updating the filter triggers a refresh of the subnets
        $scope.subnetFilter = {
          type: 'openstack',
          account: $scope.loadBalancer.account,
          region: $scope.loadBalancer.region,
        };
      };

      this.onDistributionChanged = function(distribution) {
        $scope.loadBalancer.algorithm = distribution;
      };

      this.newStatusCode = 200;
      this.addStatusCode = function() {
        var newCode = parseInt(this.newStatusCode);
        if (!$scope.loadBalancer.healthMonitor.expectedCodes.includes(newCode)) {
          $scope.loadBalancer.healthMonitor.expectedCodes.push(newCode);
          $scope.loadBalancer.healthMonitor.expectedCodes.sort();
        }
      };

      this.removeStatusCode = function(code) {
        $scope.loadBalancer.healthMonitor.expectedCodes = $scope.loadBalancer.healthMonitor.expectedCodes.filter(
          function(c) {
            return c !== code;
          },
        );
      };

      this.prependForwardSlash = text => {
        return text && text.indexOf('/') !== 0 ? `/${text}` : text;
      };

      this.removeListener = function(index) {
        $scope.loadBalancer.listeners.splice(index, 1);
      };

      this.addListener = function() {
        $scope.loadBalancer.listeners.push({ externalProtocol: 'HTTP', externalPort: 80, internalPort: 80 });
      };

      this.listenerProtocolChanged = listener => {
        if (listener.externalProtocol === 'TERMINATED_HTTPS') {
          listener.externalPort = 443;
          listener.internalPort = 443;
        }
        if (listener.externalProtocol === 'HTTP') {
          listener.externalPort = 80;
          listener.internalPort = 80;
        }
        if (listener.externalProtocol === 'TCP') {
          listener.externalPort = '';
          listener.internalPort = '';
        }
      };

      this.showSslCertificateIdField = function() {
        return $scope.loadBalancer.listeners.some(function(listener) {
          return listener.externalProtocol === 'TERMINATED_HTTPS';
        });
      };

      this.submit = function() {
        var descriptor = isNew ? 'Create' : 'Update';

        this.updateName();
        $scope.taskMonitor.submit(function() {
          let params = {
            cloudProvider: 'openstack',
            account: $scope.loadBalancer.accountId || $scope.loadBalancer.account,
            accountId: $scope.loadBalancer.accountId,
            securityGroups: $scope.loadBalancer.securityGroups,
          };
          return LoadBalancerWriter.upsertLoadBalancer(
            _.omit($scope.loadBalancer, 'accountId'),
            application,
            descriptor,
            params,
          );
        });
      };

      this.cancel = function() {
        $uibModalInstance.dismiss();
      };
    },
  ]);

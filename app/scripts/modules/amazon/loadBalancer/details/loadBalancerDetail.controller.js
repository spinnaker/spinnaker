'use strict';

import _ from 'lodash';
let angular = require('angular');

import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {LOAD_BALANCER_READ_SERVICE} from 'core/loadBalancer/loadBalancer.read.service';
import {LOAD_BALANCER_WRITE_SERVICE} from 'core/loadBalancer/loadBalancer.write.service';
import {SUBNET_READ_SERVICE} from 'core/subnet/subnet.read.service';
import {SECURITY_GROUP_READER} from 'core/securityGroup/securityGroupReader.service';

module.exports = angular.module('spinnaker.loadBalancer.aws.details.controller', [
  require('angular-ui-router'),
  SECURITY_GROUP_READER,
  LOAD_BALANCER_WRITE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  require('core/presentation/collapsibleSection/collapsibleSection.directive.js'),
  require('core/utils/selectOnDblClick.directive.js'),
  SUBNET_READ_SERVICE,
])
  .controller('awsLoadBalancerDetailsCtrl', function ($scope, $state, $uibModal, $q, loadBalancer, app,
                                                      securityGroupReader, confirmationModalService, loadBalancerWriter,
                                                      loadBalancerReader, subnetReader) {

    $scope.state = {
      loading: true
    };

    this.application = app;

    let extractLoadBalancer = () => {
      let appLoadBalancer = app.loadBalancers.data.find(function (test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId;
      });

      if (appLoadBalancer) {
        var detailsLoader = loadBalancerReader.getLoadBalancerDetails('aws', loadBalancer.accountId, loadBalancer.region, loadBalancer.name);
        return detailsLoader.then((details) => {
          $scope.loadBalancer = appLoadBalancer;
          $scope.state.loading = false;
          var securityGroups = [];
          if (details.length) {
            $scope.loadBalancer.elb = details[0];
            $scope.loadBalancer.account = loadBalancer.accountId;
            if (details[0].listenerDescriptions) {
              this.elbProtocol = 'http:';
              if (details[0].listenerDescriptions.some(l => l.listener.protocol === 'HTTPS')) {
                this.elbProtocol = 'https:';
              }
            }

            if ($scope.loadBalancer.elb.availabilityZones) {
              $scope.loadBalancer.elb.availabilityZones.sort();
            }

            $scope.loadBalancer.elb.securityGroups.forEach((securityGroupId) => {
              var match = securityGroupReader.getApplicationSecurityGroup(app, loadBalancer.accountId, loadBalancer.region, securityGroupId);
              if (match) {
                securityGroups.push(match);
              }
            });
            $scope.securityGroups = _.sortBy(securityGroups, 'name');

            if ($scope.loadBalancer.subnets) {
              $scope.loadBalancer.subnetDetails = $scope.loadBalancer.subnets.reduce( (detailList, subnetId) => {
                subnetReader.getSubnetByIdAndProvider(subnetId, $scope.loadBalancer.provider)
                  .then( (subnetDetail) => {
                    detailList.push(subnetDetail);
                  });

                return detailList;
              }, []);
            }
          }
        },
          autoClose
        );
      } else {
        autoClose();
      }
      if (!$scope.loadBalancer) {
        autoClose();
      }

      return $q.when(null);
    };

    function autoClose() {
      if ($scope.$$destroyed) {
        return;
      }
      $state.params.allowModalToStayOpen = true;
      $state.go('^', null, {location: 'replace'});
    }

    app.ready().then(extractLoadBalancer).then(() => {
      // If the user navigates away from the view before the initial extractLoadBalancer call completes,
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed) {
        app.getDataSource('loadBalancers').onRefresh($scope, extractLoadBalancer);
      }
    });

    this.getFirstSubnetPurpose = function(subnetDetailsList = []) {
      return _.head(subnetDetailsList.map(subnet => subnet.purpose)) || '';
    };

    this.editLoadBalancer = function editLoadBalancer() {
      $uibModal.open({
        templateUrl: require('../configure/editLoadBalancer.html'),
        controller: 'awsCreateLoadBalancerCtrl as ctrl',
        size: 'lg',
        resolve: {
          application: function() { return app; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); },
          isNew: function() { return false; },
          forPipelineConfig: function() { return false; }
        }
      });
    };

    this.cloneLoadBalancer = function () {
      $uibModal.open({
        templateUrl: require('../configure/createLoadBalancer.html'),
        controller: 'awsCreateLoadBalancerCtrl as ctrl',
        size: 'lg',
        resolve: {
          application: function() { return app; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); },
          isNew: function() { return true; },
          forPipelineConfig: function() { return false; }
        }
      });
    };

    this.deleteLoadBalancer = function deleteLoadBalancer() {
      if ($scope.loadBalancer.instances && $scope.loadBalancer.instances.length) {
        return;
      }

      const taskMonitor = {
        application: app,
        title: 'Deleting ' + loadBalancer.name,
      };

      const command = {
        cloudProvider: $scope.loadBalancer.cloudProvider,
        loadBalancerName: $scope.loadBalancer.name,
        regions: [$scope.loadBalancer.region],
        credentials: $scope.loadBalancer.account,
      };

      const submitMethod = () => loadBalancerWriter.deleteLoadBalancer(command, app);

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        provider: 'aws',
        account: loadBalancer.accountId,
        applicationName: app.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);

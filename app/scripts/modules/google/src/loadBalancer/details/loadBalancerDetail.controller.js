'use strict';

const angular = require('angular');
import _ from 'lodash';

import { ACCOUNT_SERVICE, LOAD_BALANCER_READ_SERVICE, LOAD_BALANCER_WRITE_SERVICE } from '@spinnaker/core';
import { GCE_HTTP_LOAD_BALANCER_UTILS } from 'google/loadBalancer/httpLoadBalancerUtils.service';
import { GCE_LOAD_BALANCER_TYPE_TO_WIZARD_CONSTANT } from '../configure/choice/loadBalancerTypeToWizardMap.constant';
import { GCE_BACKEND_SERVICE_DETAILS_COMPONENT } from './backendService/backendService.component';
import { SESSION_AFFINITY_FILTER } from './backendService/sessionAffinity.filter';

import { DELETE_MODAL_CONTROLLER } from './deleteModal/deleteModal.controller';

module.exports = angular.module('spinnaker.loadBalancer.gce.details.controller', [
  require('@uirouter/angularjs').default,
  ACCOUNT_SERVICE,
  LOAD_BALANCER_WRITE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  require('google/common/xpnNaming.gce.service.js'),
  require('./hostAndPathRules/hostAndPathRulesButton.component.js'),
  require('./loadBalancerType/loadBalancerType.component.js'),
  GCE_HTTP_LOAD_BALANCER_UTILS,
  require('./healthCheck/healthCheck.component.js'),
  GCE_BACKEND_SERVICE_DETAILS_COMPONENT,
  DELETE_MODAL_CONTROLLER,
  SESSION_AFFINITY_FILTER,
  GCE_LOAD_BALANCER_TYPE_TO_WIZARD_CONSTANT,
])
  .controller('gceLoadBalancerDetailsCtrl', function ($scope, $state, $uibModal, loadBalancer, app,
                                                      accountService, gceHttpLoadBalancerUtils,
                                                      loadBalancerWriter, loadBalancerReader,
                                                      $q, loadBalancerTypeToWizardMap, gceXpnNamingService) {

    let application = this.application = app;

    $scope.state = {
      loading: true
    };

    function extractLoadBalancer() {
      $scope.loadBalancer = application.loadBalancers.data.filter(function (test) {
        var testVpc = test.vpcId || null;
        return test.name === loadBalancer.name && (test.region === loadBalancer.region || test.region === 'global') && test.account === loadBalancer.accountId && testVpc === loadBalancer.vpcId;
      })[0];

      if ($scope.loadBalancer) {
        return createDetailsLoader().then(function(details) {
          $scope.state.loading = false;
          var filtered = details.filter(function(test) {
            return test.vpcid === loadBalancer.vpcId || (!test.vpcid && !loadBalancer.vpcId);
          });
          if (filtered.length) {
            $scope.loadBalancer.elb = filtered[0];
            $scope.loadBalancer.account = loadBalancer.accountId;

            accountService.getCredentialsKeyedByAccount('gce').then(function() {
              if (gceHttpLoadBalancerUtils.isHttpLoadBalancer($scope.loadBalancer)) {
                $scope.loadBalancer.elb.backendServices = getBackendServices($scope.loadBalancer);
                $scope.loadBalancer.elb.healthChecks = _.chain($scope.loadBalancer.elb.backendServices)
                  .map('healthCheck')
                  .uniqBy('name')
                  .value();
              }
            });
          }
          accountService.getAccountDetails(loadBalancer.accountId).then(function(accountDetails) {
            let resourceTypes;

            if ($scope.loadBalancer.loadBalancerType === 'INTERNAL') {
              resourceTypes = ['gce_forwarding_rule', 'gce_backend_service'];
            } else if ($scope.loadBalancer.loadBalancerType === 'NETWORK') {
              resourceTypes = ['gce_forwarding_rule', 'gce_target_pool', 'gce_health_check'];
            } else if ($scope.loadBalancer.loadBalancerType === 'SSL') {
              resourceTypes = ['gce_forwarding_rule', 'gce_backend_service'];
            } else if ($scope.loadBalancer.loadBalancerType === 'TCP') {
              resourceTypes = ['gce_forwarding_rule', 'gce_backend_service'];
            } else {
              // $scope.loadBalancer.loadBalancerType === 'HTTP'
              resourceTypes = ['http_load_balancer', 'gce_target_http_proxy', 'gce_url_map', 'gce_backend_service'];
            }

            resourceTypes = _.join(resourceTypes, ' OR ');

            $scope.loadBalancer.project = accountDetails.project;
            $scope.loadBalancer.logsLink =
              'https://console.developers.google.com/project/' + accountDetails.project + '/logs?advancedFilter=resource.type=(' + resourceTypes + ')%0A\"' + $scope.loadBalancer.name + '\"';
          });
        },
          autoClose
        );
      }
      if (!$scope.loadBalancer) {
        autoClose();
      }
      return $q.when(null);
    }

    function createDetailsLoader () {
      if (gceHttpLoadBalancerUtils.isHttpLoadBalancer($scope.loadBalancer)) {
        var detailsPromises = $scope.loadBalancer.listeners.map((listener) => {
          return loadBalancerReader
            .getLoadBalancerDetails($scope.loadBalancer.provider, loadBalancer.accountId, $scope.loadBalancer.region, listener.name);
        });

        return $q.all(detailsPromises)
          .then((loadBalancers) => {
            loadBalancers = _.flatten(loadBalancers);
            var representativeLb = loadBalancers[0];
            representativeLb.dns = loadBalancers.map((loadBalancer) => {
              var protocol;
              if (loadBalancer.listenerDescriptions[0].listener.loadBalancerPort === '443') {
                protocol = 'https:';
              } else {
                protocol = 'http:';
              }
              return {dnsname: loadBalancer.dnsname, protocol: protocol};
            });
            representativeLb.dns = _.uniqBy(representativeLb.dns, 'dnsname');
            representativeLb.listenerDescriptions = _.flatten(loadBalancers.map((lb) => lb.listenerDescriptions));
            return [representativeLb];
          });

      } else {
        return loadBalancerReader
          .getLoadBalancerDetails($scope.loadBalancer.provider, loadBalancer.accountId, $scope.loadBalancer.region, $scope.loadBalancer.name)
          .then((loadBalancerDetails) => {
            var loadBalancer = loadBalancerDetails[0];
            var protocol;
            if (loadBalancer.listenerDescriptions[0].listener.loadBalancerPort === '443') {
              protocol = 'https:';
            } else {
              protocol = 'http:';
            }
            loadBalancer.dns = {dnsname: loadBalancer.dnsname, protocol: protocol};
            return loadBalancerDetails;
          });
      }
    }

    function getBackendServices (loadBalancer) {
      var backendServices = [loadBalancer.defaultService];

      if (loadBalancer.hostRules.length) {
        backendServices = _.chain(loadBalancer.hostRules)
          .reduce((services, hostRule) => {
            services.push(hostRule.pathMatcher.defaultService);
            return services.concat(_.map(hostRule.pathMatcher.pathRules, 'backendService'));
          }, backendServices)
          .uniqBy('name')
          .value();
      }
      return backendServices;
    }

    function autoClose() {
      if ($scope.$$destroyed) {
        return;
      }
      $state.params.allowModalToStayOpen = true;
      $state.go('^', null, {location: 'replace'});
    }

    app.loadBalancers.ready().then(extractLoadBalancer).then(() => {
      // If the user navigates away from the view before the initial extractLoadBalancer call completes,
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed) {
        app.loadBalancers.onRefresh($scope, extractLoadBalancer);
      }
    });

    this.editLoadBalancer = function editLoadBalancer() {
      let wizard = loadBalancerTypeToWizardMap[$scope.loadBalancer.loadBalancerType];

      $uibModal.open({
        templateUrl: wizard.editTemplateUrl,
        controller: `${wizard.controller} as ctrl`,
        size: 'lg',
        resolve: {
          application: function() { return application; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); },
          isNew: function() { return false; }
        }
      });
    };

    this.deleteLoadBalancer = function deleteLoadBalancer() {
      if (!($scope.loadBalancer.instances && $scope.loadBalancer.instances.length)) {
        $uibModal.open({
          controller: 'gceLoadBalancerDeleteModalCtrl as ctrl',
          templateUrl: require('./deleteModal/deleteModal.html'),
          resolve: {
            application: () => application,
            loadBalancer: () => $scope.loadBalancer,
          },
        });
      }
    };

    this.isHttpLoadBalancer = (lb) => gceHttpLoadBalancerUtils.isHttpLoadBalancer(lb);

    this.getNetworkId = function getNetworkId(loadBalancer) {
      return gceXpnNamingService.decorateXpnResourceIfNecessary(loadBalancer.project, loadBalancer.network);
    };

    this.getSubnetId = function getSubnetId(loadBalancer) {
      return gceXpnNamingService.decorateXpnResourceIfNecessary(loadBalancer.project, loadBalancer.subnet);
    };
  }
);

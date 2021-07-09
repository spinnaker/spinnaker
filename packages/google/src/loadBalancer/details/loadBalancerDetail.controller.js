'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import _ from 'lodash';

import { AccountService, LOAD_BALANCER_READ_SERVICE } from '@spinnaker/core';

import { GCE_BACKEND_SERVICE_DETAILS_COMPONENT } from './backendService/backendService.component';
import { SESSION_AFFINITY_FILTER } from './backendService/sessionAffinity.filter';
import { GOOGLE_COMMON_XPNNAMING_GCE_SERVICE } from '../../common/xpnNaming.gce.service';
import { GCE_LOAD_BALANCER_TYPE_TO_WIZARD_CONSTANT } from '../configure/choice/loadBalancerTypeToWizardMap.constant';
import { DELETE_MODAL_CONTROLLER } from './deleteModal/deleteModal.controller';
import { GOOGLE_LOADBALANCER_DETAILS_HEALTHCHECK_HEALTHCHECK_COMPONENT } from './healthCheck/healthCheck.component';
import { GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULESBUTTON_COMPONENT } from './hostAndPathRules/hostAndPathRulesButton.component';
import { GCE_HTTP_LOAD_BALANCER_UTILS } from '../httpLoadBalancerUtils.service';
import { GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERTYPE_LOADBALANCERTYPE_COMPONENT } from './loadBalancerType/loadBalancerType.component';

export const GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER =
  'spinnaker.loadBalancer.gce.details.controller';
export const name = GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER, [
    UIROUTER_ANGULARJS,
    LOAD_BALANCER_READ_SERVICE,
    GOOGLE_COMMON_XPNNAMING_GCE_SERVICE,
    GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULESBUTTON_COMPONENT,
    GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERTYPE_LOADBALANCERTYPE_COMPONENT,
    GCE_HTTP_LOAD_BALANCER_UTILS,
    GOOGLE_LOADBALANCER_DETAILS_HEALTHCHECK_HEALTHCHECK_COMPONENT,
    GCE_BACKEND_SERVICE_DETAILS_COMPONENT,
    DELETE_MODAL_CONTROLLER,
    SESSION_AFFINITY_FILTER,
    GCE_LOAD_BALANCER_TYPE_TO_WIZARD_CONSTANT,
  ])
  .controller('gceLoadBalancerDetailsCtrl', [
    '$scope',
    '$state',
    '$uibModal',
    'loadBalancer',
    'app',
    'gceHttpLoadBalancerUtils',
    'loadBalancerReader',
    '$q',
    'loadBalancerTypeToWizardMap',
    'gceXpnNamingService',
    function (
      $scope,
      $state,
      $uibModal,
      loadBalancer,
      app,
      gceHttpLoadBalancerUtils,
      loadBalancerReader,
      $q,
      loadBalancerTypeToWizardMap,
      gceXpnNamingService,
    ) {
      const application = (this.application = app);

      $scope.state = {
        loading: true,
      };

      function extractLoadBalancer() {
        $scope.loadBalancer = application.loadBalancers.data.filter(function (test) {
          const testVpc = test.vpcId || null;
          return (
            test.name === loadBalancer.name &&
            (test.region === loadBalancer.region || test.region === 'global') &&
            test.account === loadBalancer.accountId &&
            testVpc === loadBalancer.vpcId
          );
        })[0];

        if ($scope.loadBalancer) {
          return createDetailsLoader().then(function (details) {
            $scope.state.loading = false;
            const filtered = details.filter(function (test) {
              return test.vpcid === loadBalancer.vpcId || (!test.vpcid && !loadBalancer.vpcId);
            });
            if (filtered.length) {
              $scope.loadBalancer.elb = filtered[0];
              $scope.loadBalancer.account = loadBalancer.accountId;

              AccountService.getCredentialsKeyedByAccount('gce').then(function () {
                if (gceHttpLoadBalancerUtils.isHttpLoadBalancer($scope.loadBalancer)) {
                  $scope.loadBalancer.elb.backendServices = getBackendServices($scope.loadBalancer);
                  $scope.loadBalancer.elb.healthChecks = _.chain($scope.loadBalancer.elb.backendServices)
                    .map('healthCheck')
                    .uniqBy('name')
                    .value();
                }
              });
            }
            AccountService.getAccountDetails(loadBalancer.accountId).then(function (accountDetails) {
              let resourceTypes;

              if ($scope.loadBalancer.loadBalancerType === 'INTERNAL') {
                resourceTypes = ['gce_forwarding_rule', 'gce_backend_service'];
              } else if ($scope.loadBalancer.loadBalancerType === 'NETWORK') {
                resourceTypes = ['gce_forwarding_rule', 'gce_target_pool', 'gce_health_check'];
              } else if ($scope.loadBalancer.loadBalancerType === 'SSL') {
                resourceTypes = ['gce_forwarding_rule', 'gce_backend_service'];
              } else if ($scope.loadBalancer.loadBalancerType === 'TCP') {
                resourceTypes = ['gce_forwarding_rule', 'gce_backend_service'];
              } else if ($scope.loadBalancer.loadBalancerType === 'INTERNAL_MANAGED') {
                resourceTypes = ['http_load_balancer', 'gce_target_http_proxy', 'gce_url_map', 'gce_backend_service'];
              } else {
                // $scope.loadBalancer.loadBalancerType === 'HTTP'
                resourceTypes = ['http_load_balancer', 'gce_target_http_proxy', 'gce_url_map', 'gce_backend_service'];
              }

              resourceTypes = _.join(resourceTypes, ' OR ');

              $scope.loadBalancer.project = accountDetails.project;
              $scope.loadBalancer.logsLink =
                'https://console.developers.google.com/project/' +
                accountDetails.project +
                '/logs?advancedFilter=resource.type=(' +
                resourceTypes +
                ')%0A"' +
                $scope.loadBalancer.name +
                '"';
            });
          }, autoClose);
        }
        if (!$scope.loadBalancer) {
          autoClose();
        }
        return $q.when(null);
      }

      function createDetailsLoader() {
        if (gceHttpLoadBalancerUtils.isHttpLoadBalancer($scope.loadBalancer)) {
          const detailsPromises = $scope.loadBalancer.listeners.map((listener) => {
            return loadBalancerReader.getLoadBalancerDetails(
              $scope.loadBalancer.provider,
              loadBalancer.accountId,
              $scope.loadBalancer.region,
              listener.name,
            );
          });

          return $q.all(detailsPromises).then((loadBalancers) => {
            loadBalancers = _.flatten(loadBalancers);
            const representativeLb = loadBalancers[0];
            representativeLb.dns = loadBalancers.map((loadBalancer) => {
              let protocol;
              if (loadBalancer.listenerDescriptions[0].listener.loadBalancerPort === '443') {
                protocol = 'https:';
              } else {
                protocol = 'http:';
              }
              return { dnsname: loadBalancer.dnsname, protocol: protocol };
            });
            representativeLb.dns = _.uniqBy(representativeLb.dns, 'dnsname');
            representativeLb.listenerDescriptions = _.flatten(loadBalancers.map((lb) => lb.listenerDescriptions));
            return [representativeLb];
          });
        } else {
          return loadBalancerReader
            .getLoadBalancerDetails(
              $scope.loadBalancer.provider,
              loadBalancer.accountId,
              $scope.loadBalancer.region,
              $scope.loadBalancer.name,
            )
            .then((loadBalancerDetails) => {
              const loadBalancer = loadBalancerDetails[0];
              let protocol;
              if (loadBalancer.listenerDescriptions[0].listener.loadBalancerPort === '443') {
                protocol = 'https:';
              } else {
                protocol = 'http:';
              }
              loadBalancer.dns = { dnsname: loadBalancer.dnsname, protocol: protocol };
              return loadBalancerDetails;
            });
        }
      }

      function getBackendServices(loadBalancer) {
        let backendServices = [loadBalancer.defaultService];

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
        $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
      }

      app.loadBalancers
        .ready()
        .then(extractLoadBalancer)
        .then(() => {
          // If the user navigates away from the view before the initial extractLoadBalancer call completes,
          // do not bother subscribing to the refresh
          if (!$scope.$$destroyed) {
            app.loadBalancers.onRefresh($scope, extractLoadBalancer);
          }
        });

      this.editLoadBalancer = function editLoadBalancer() {
        const wizard = loadBalancerTypeToWizardMap[$scope.loadBalancer.loadBalancerType];

        $uibModal.open({
          templateUrl: wizard.editTemplateUrl,
          controller: `${wizard.controller} as ctrl`,
          size: 'lg',
          resolve: {
            application: function () {
              return application;
            },
            loadBalancer: function () {
              return angular.copy($scope.loadBalancer);
            },
            isNew: function () {
              return false;
            },
          },
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
    },
  ]);

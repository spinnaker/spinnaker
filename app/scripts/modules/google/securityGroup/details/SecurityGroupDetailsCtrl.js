/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.gce.details.controller', [
  require('angular-ui-router'),
  require('../../../core/account/account.service.js'),
  require('../../../core/securityGroup/securityGroup.read.service.js'),
  require('../../../core/securityGroup/securityGroup.write.service.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('../clone/cloneSecurityGroup.controller.js'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
])
  .controller('gceSecurityGroupDetailsCtrl', function ($scope, $state, resolvedSecurityGroup, accountService, app, InsightFilterStateModel,
                                                    confirmationModalService, securityGroupWriter, securityGroupReader,
                                                    $uibModal, _) {

    const application = app;
    const securityGroup = resolvedSecurityGroup;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractSecurityGroup() {
      return securityGroupReader.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.provider, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
        $scope.state.loading = false;

        if (!details || _.isEmpty( details.plain())) {
          fourOhFour();
        } else {
          $scope.securityGroup = details;

          $scope.securityGroup.sourceRanges = _.uniq(
            _.map($scope.securityGroup.ipRangeRules, (rule) => rule.range.ip + rule.range.cidr)
          );

          let ipIngress = _.map($scope.securityGroup.ipRangeRules, function(ipRangeRule) {
            return {
              protocol: ipRangeRule.protocol,
              portRanges: ipRangeRule.portRanges,
            };
          });

          let ipIngressRules = {};

          ipIngress.forEach(function(ipIngressRule) {
            if (_.has(ipIngressRules, ipIngressRule.protocol)) {
              ipIngressRules[ipIngressRule.protocol] = ipIngressRules[ipIngressRule.protocol].concat(ipIngressRule.portRanges);

              ipIngressRules[ipIngressRule.protocol] = _.uniq(ipIngressRules[ipIngressRule.protocol], function(portRange) {
                return portRange.startPort + "->" + portRange.endPort;
              });
            } else {
              ipIngressRules[ipIngressRule.protocol] = ipIngressRule.portRanges;
            }
          });

          ipIngressRules = _.map(ipIngressRules, function(portRanges, protocol) {
            return {
              protocol: protocol,
              portRanges: portRanges,
            };
          });

          $scope.securityGroup.ipIngressRules = ipIngressRules;

          $scope.securityGroup.protocolPortRangeCount = _.sum(ipIngressRules, function(ipIngressRule) {
            return ipIngressRule.portRanges.length > 1  ? ipIngressRule.portRanges.length : 1;
          });

          if ($scope.securityGroup.targetTags) {
            $scope.securityGroup.targetTagsDescription = $scope.securityGroup.targetTags.join(", ");
          }

          accountService.getAccountDetails(securityGroup.accountId).then(function(accountDetails) {
            $scope.securityGroup.logsLink =
              'https://console.developers.google.com/project/' + accountDetails.projectName + '/logs?service=compute.googleapis.com&minLogLevel=0&filters=text:' + securityGroup.name;
          });
        }
      },
        fourOhFour
      );
    }

    function fourOhFour() {
      if ($scope.$$destroyed) {
        return;
      }
      $state.params.allowModalToStayOpen = true;
      $state.go('^', null, {location: 'replace'});
    }

    extractSecurityGroup().then(() => {
      // If the user navigates away from the view before the initial extractSecurityGroup call completes,
      // do not bother subscribing to the autoRefreshStream
      if (!$scope.$$destroyed) {
        let refreshWatcher = app.autoRefreshStream.subscribe(extractSecurityGroup);
        $scope.$on('$destroy', () => refreshWatcher.dispose());
      }
    });

    this.editInboundRules = function editInboundRules() {
      $uibModal.open({
        templateUrl: require('../configure/editSecurityGroup.html'),
        controller: 'gceEditSecurityGroupCtrl as ctrl',
        resolve: {
          securityGroup: function() {
            return angular.copy($scope.securityGroup.plain());
          },
          application: function() { return application; }
        }
      });
    };


    this.cloneSecurityGroup = function cloneSecurityGroup() {
      $uibModal.open({
        templateUrl: require('../clone/cloneSecurityGroup.html'),
        controller: 'gceCloneSecurityGroupController as ctrl',
        resolve: {
          securityGroup: function() {
            var securityGroup = angular.copy($scope.securityGroup.plain());
            if(securityGroup.region) {
              securityGroup.regions = [securityGroup.region];
            }
            return securityGroup;
          },
          application: function() { return application; }
        }
      });
    };

    this.deleteSecurityGroup = function deleteSecurityGroup() {
      var taskMonitor = {
        application: application,
        title: 'Deleting ' + securityGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        securityGroup.providerType = $scope.securityGroup.type;
        return securityGroupWriter.deleteSecurityGroup(securityGroup, application, {
          cloudProvider: $scope.securityGroup.type,
          securityGroupName: securityGroup.name,
        });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + securityGroup.name + '?',
        buttonText: 'Delete ' + securityGroup.name,
        destructive: true,
        provider: 'gce',
        account: securityGroup.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);

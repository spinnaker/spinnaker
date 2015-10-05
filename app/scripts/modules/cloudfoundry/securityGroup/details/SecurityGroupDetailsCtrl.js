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

module.exports = angular.module('spinnaker.securityGroup.cf.details.controller', [
  require('angular-ui-router'),
  require('../../../core/account/account.service.js'),
  require('../../../securityGroups/securityGroup.read.service.js'),
  require('../../../securityGroups/securityGroup.write.service.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../utils/lodash.js'),
  require('../../../insight/insightFilterState.model.js'),
  require('../../../utils/selectOnDblClick.directive.js'),
])
  .controller('cfSecurityGroupDetailsCtrl', function ($scope, $state, resolvedSecurityGroup, accountService, app, InsightFilterStateModel,
                                                    confirmationModalService, securityGroupWriter, securityGroupReader,
                                                    $modal, _) {

    const application = app;
    const securityGroup = resolvedSecurityGroup;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractSecurityGroup() {
      securityGroupReader.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.provider, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
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
      function() {
        fourOhFour();
      });
    }

    function fourOhFour() {
      $state.go('^');
    }

    extractSecurityGroup();

    application.registerAutoRefreshHandler(extractSecurityGroup, $scope);

  }
).name;

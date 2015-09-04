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
  require('../../../account/accountService.js'),
  require('../../../securityGroups/securityGroup.read.service.js'),
  require('../../../securityGroups/securityGroup.write.service.js'),
  require('../../../confirmationModal/confirmationModal.service.js'),
  require('../../../utils/lodash.js'),
  require('../../../insight/insightFilterState.model.js'),
  require('../clone/cloneSecurityGroup.controller.js'),
])
  .controller('gceSecurityGroupDetailsCtrl', function ($scope, $state, resolvedSecurityGroup, accountService, app, InsightFilterStateModel,
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

    this.editInboundRules = function editInboundRules() {
      $modal.open({
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
      $modal.open({
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
        return securityGroupWriter.deleteSecurityGroup($scope.securityGroup, application);
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
).name;

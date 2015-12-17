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
  require('../../../core/securityGroup/securityGroup.read.service.js'),
  require('../../../core/securityGroup/securityGroup.write.service.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
])
  .controller('cfSecurityGroupDetailsCtrl', function ($scope, $state, resolvedSecurityGroup, accountService, app, InsightFilterStateModel,
                                                    confirmationModalService, securityGroupWriter, securityGroupReader,
                                                    $uibModal, _) {

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

  }
);

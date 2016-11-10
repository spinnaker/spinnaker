'use strict';

import _ from 'lodash';
import {ACCOUNT_SERVICE} from 'core/account/account.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.cf.details.controller', [
  require('angular-ui-router'),
  ACCOUNT_SERVICE,
  require('core/securityGroup/securityGroup.read.service.js'),
  require('core/securityGroup/securityGroup.write.service.js'),
  require('core/confirmationModal/confirmationModal.service.js'),
  require('core/insight/insightFilterState.model.js'),
  require('core/utils/selectOnDblClick.directive.js'),
])
  .controller('cfSecurityGroupDetailsCtrl', function ($scope, $state, resolvedSecurityGroup, accountService, app, InsightFilterStateModel,
                                                    confirmationModalService, securityGroupWriter, securityGroupReader) {

    const application = app;
    const securityGroup = resolvedSecurityGroup;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractSecurityGroup() {
      securityGroupReader.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.provider, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
        $scope.state.loading = false;

        if (!details || _.isEmpty( details ) ) {
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
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed) {
        app.securityGroups.onRefresh($scope, extractSecurityGroup);
      }
    });

    if (app.isStandalone) {
      // we still want the edit to refresh the security group details when the modal closes
      app.securityGroups = {
        refresh: extractSecurityGroup
      };
    }
  }
);

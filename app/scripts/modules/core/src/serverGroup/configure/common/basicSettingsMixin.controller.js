'use strict';

import _ from 'lodash';

import { IMAGE_READER } from 'core/image/image.reader';
import { NameUtils } from 'core/naming';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';

import { module } from 'angular';

export const CORE_SERVERGROUP_CONFIGURE_COMMON_BASICSETTINGSMIXIN_CONTROLLER =
  'spinnaker.core.serverGroup.basicSettings.controller';
export const name = CORE_SERVERGROUP_CONFIGURE_COMMON_BASICSETTINGSMIXIN_CONTROLLER; // for backwards compatibility
module(CORE_SERVERGROUP_CONFIGURE_COMMON_BASICSETTINGSMIXIN_CONTROLLER, [
  ANGULAR_UI_BOOTSTRAP,
  UIROUTER_ANGULARJS,
  IMAGE_READER,
]).controller('BasicSettingsMixin', [
  '$scope',
  'imageReader',
  '$uibModalStack',
  '$state',
  function($scope, imageReader, $uibModalStack, $state) {
    this.createsNewCluster = function() {
      let name = this.getNamePreview();
      $scope.latestServerGroup = this.getLatestServerGroup();
      return !_.find($scope.application.clusters, { name: name });
    };

    this.getNamePreview = function() {
      let command = $scope.command;
      if (!command) {
        return '';
      }
      return NameUtils.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
    };

    this.getLatestServerGroup = function() {
      let command = $scope.command;
      let cluster = NameUtils.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
      let inCluster = $scope.application.serverGroups.data
        .filter(function(serverGroup) {
          return (
            serverGroup.cluster === cluster &&
            serverGroup.account === command.credentials &&
            serverGroup.region === command.region
          );
        })
        .sort(function(a, b) {
          return a.createdTime - b.createdTime;
        });
      return inCluster.length ? inCluster.pop() : null;
    };

    this.showPreviewAsWarning = function() {
      let mode = $scope.command.viewState.mode;
      let createsNewCluster = this.createsNewCluster();

      return (mode === 'create' && !createsNewCluster) || (mode !== 'create' && createsNewCluster);
    };

    this.navigateToLatestServerGroup = function() {
      let latest = $scope.latestServerGroup;
      let params = {
        provider: $scope.command.selectedProvider,
        accountId: latest.account,
        region: latest.region,
        serverGroup: latest.name,
      };

      $uibModalStack.dismissAll();
      if ($state.is('home.applications.application.insight.clusters')) {
        $state.go('.serverGroup', params);
      } else {
        $state.go('^.serverGroup', params);
      }
    };

    this.stackPattern = {
      test: function(stack) {
        let pattern = $scope.command.viewState.templatingEnabled
          ? /^([a-zA-Z_0-9._${}]*(\${.+})*)*$/
          : /^[a-zA-Z_0-9._${}]*$/;

        return isNotExpressionLanguage(stack) ? pattern.test(stack) : true;
      },
    };

    this.detailPattern = {
      test: function(detail) {
        let pattern = $scope.command.viewState.templatingEnabled
          ? /^([a-zA-Z_0-9._${}-]*(\${.+})*)*$/
          : /^[a-zA-Z_0-9._${}-]*$/;

        return isNotExpressionLanguage(detail) ? pattern.test(detail) : true;
      },
    };

    let isNotExpressionLanguage = field => {
      return field && !field.includes('${');
    };
  },
]);

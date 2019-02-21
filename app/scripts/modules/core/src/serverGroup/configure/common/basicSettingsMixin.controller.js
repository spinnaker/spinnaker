'use strict';

import _ from 'lodash';

import { IMAGE_READER } from 'core/image/image.reader';
import { NameUtils } from 'core/naming';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.serverGroup.basicSettings.controller', [
    require('angular-ui-bootstrap'),
    require('@uirouter/angularjs').default,
    IMAGE_READER,
  ])
  .controller('BasicSettingsMixin', [
    '$scope',
    'imageReader',
    '$uibModalStack',
    '$state',
    function($scope, imageReader, $uibModalStack, $state) {
      this.createsNewCluster = function() {
        var name = this.getNamePreview();
        $scope.latestServerGroup = this.getLatestServerGroup();
        return !_.find($scope.application.clusters, { name: name });
      };

      this.getNamePreview = function() {
        var command = $scope.command;
        if (!command) {
          return '';
        }
        return NameUtils.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
      };

      this.getLatestServerGroup = function() {
        var command = $scope.command;
        var cluster = NameUtils.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
        var inCluster = $scope.application.serverGroups.data
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
        var mode = $scope.command.viewState.mode,
          createsNewCluster = this.createsNewCluster();

        return (mode === 'create' && !createsNewCluster) || (mode !== 'create' && createsNewCluster);
      };

      this.navigateToLatestServerGroup = function() {
        var latest = $scope.latestServerGroup,
          params = {
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
          var pattern = $scope.command.viewState.templatingEnabled
            ? /^([a-zA-Z_0-9._${}]*(\${.+})*)*$/
            : /^[a-zA-Z_0-9._${}]*$/;

          return isNotExpressionLanguage(stack) ? pattern.test(stack) : true;
        },
      };

      this.detailPattern = {
        test: function(detail) {
          var pattern = $scope.command.viewState.templatingEnabled
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

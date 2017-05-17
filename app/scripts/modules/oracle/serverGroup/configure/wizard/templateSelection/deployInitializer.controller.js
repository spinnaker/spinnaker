'use strict';

const angular = require('angular');
import _ from 'lodash';

import { SERVER_GROUP_READER } from '@spinnaker/core';

module.exports = angular.module('spinnaker.oraclebmcs.serverGroup.configure.deployInitialization.controller', [
  SERVER_GROUP_READER,
  require('../../serverGroupCommandBuilder.service.js'),
])
  .controller('oraclebmcsDeployInitializerCtrl', function($scope, oraclebmcsServerGroupCommandBuilder, serverGroupReader) {

    let controller = this;

    $scope.templates = [];
    if (!$scope.command.viewState.disableNoTemplateSelection) {
      let noTemplate = { label: 'None', serverGroup: null, cluster: null };

      $scope.command.viewState.template = noTemplate;

      $scope.templates = [ noTemplate ];
    }

    let allClusters = _.groupBy(_.filter($scope.application.serverGroups.data, { type: 'oraclebmcs' }), function(serverGroup) {
      return [serverGroup.cluster, serverGroup.account, serverGroup.region].join(':');
    });

    _.forEach(allClusters, function(cluster) {
      let latest = _.sortBy(cluster, 'name').pop();
      $scope.templates.push({
        cluster: latest.cluster,
        account: latest.account,
        region: latest.region,
        serverGroupName: latest.name,
        serverGroup: latest
      });
    });

    function applyCommandToScope(command) {
      command.viewState.disableImageSelection = true;
      command.viewState.disableStrategySelection = $scope.command.viewState.disableStrategySelection || false;
      command.viewState.imageId = null;
      command.viewState.readOnlyFields = $scope.command.viewState.readOnlyFields || {};
      command.viewState.submitButtonLabel = 'Add';
      command.viewState.hideClusterNamePreview = $scope.command.viewState.hideClusterNamePreview || false;
      command.viewState.templatingEnabled = true;
      if ($scope.command.viewState.overrides) {
        _.forOwn($scope.command.viewState.overrides, function(val, key) {
          command[key] = val;
        });
      }
      angular.copy(command, $scope.command);
    }

    function buildEmptyCommand() {
      return oraclebmcsServerGroupCommandBuilder
        .buildNewServerGroupCommand($scope.application, {mode: 'createPipeline'})
        .then(function(command) {
            applyCommandToScope(command);
        });
    }

    function buildCommandFromTemplate(serverGroup) {
      return serverGroupReader.getServerGroup($scope.application.name, serverGroup.account, serverGroup.region, serverGroup.name).then(function (details) {
        angular.extend(details, serverGroup);
        return oraclebmcsServerGroupCommandBuilder.buildServerGroupCommandFromExisting($scope.application, details, 'editPipeline').then(function (command) {
          applyCommandToScope(command);
        });
      });
    }

    function selectTemplate() {
      let selection = $scope.command.viewState.template;
      if (selection && selection.cluster && selection.serverGroup) {
        return buildCommandFromTemplate(selection.serverGroup);
      } else {
        return buildEmptyCommand();
      }
    }

    controller.useTemplate = function() {
      $scope.state.loaded = false;
      selectTemplate().then(function() {
        $scope.$emit('template-selected');
      });
    };
  });

'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.deployInitialization.controller', [
  require('../../../../core/serverGroup/serverGroup.read.service.js'),
  require('../../../../core/utils/lodash.js'),
  require('../serverGroupCommandBuilder.service.js'),
])
  .controller('azureDeployInitializerCtrl', function($scope, azureServerGroupCommandBuilder, serverGroupReader, _) {
    var controller = this;

    $scope.templates = [];
    if (!$scope.command.viewState.disableNoTemplateSelection) {
      var noTemplate = { label: 'None', serverGroup: null, cluster: null };

      $scope.command.viewState.template = noTemplate;

      $scope.templates = [ noTemplate ];
    }

    var allClusters = _.groupBy(_.filter($scope.application.serverGroups, { type: 'azure' }), function(serverGroup) {
      return [serverGroup.cluster, serverGroup.account, serverGroup.region].join(':');
    });

    _.forEach(allClusters, function(cluster) {
      var latest = _.sortBy(cluster, 'name').pop();
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
      return azureServerGroupCommandBuilder.buildNewServerGroupCommand($scope.application, {mode: 'createPipeline'}).then(function(command) {
        applyCommandToScope(command);
      });
    }

    function buildCommandFromTemplate(serverGroup) {
      return serverGroupReader.getServerGroup($scope.application.name, serverGroup.account, serverGroup.region, serverGroup.name).then(function (details) {
        angular.extend(details, serverGroup);
        return azureServerGroupCommandBuilder.buildServerGroupCommandFromExisting($scope.application, details, 'editPipeline').then(function (command) {
          applyCommandToScope(command);
          $scope.command.strategy = 'redblack';
        });
      });
    }

    controller.selectTemplate = function () {
      var selection = $scope.command.viewState.template;
      if (selection && selection.cluster && selection.serverGroup) {
        return buildCommandFromTemplate(selection.serverGroup);
      } else {
        return buildEmptyCommand();
      }
    };

    controller.useTemplate = function() {
      $scope.state.loaded = false;
      controller.selectTemplate().then(function() {
        $scope.$emit('template-selected');
      });
    };
  });

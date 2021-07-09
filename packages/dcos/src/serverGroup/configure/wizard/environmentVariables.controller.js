'use strict';

import { module } from 'angular';

export const DCOS_SERVERGROUP_CONFIGURE_WIZARD_ENVIRONMENTVARIABLES_CONTROLLER =
  'spinnaker.dcos.serverGroup.configure.environmentVariables';
export const name = DCOS_SERVERGROUP_CONFIGURE_WIZARD_ENVIRONMENTVARIABLES_CONTROLLER; // for backwards compatibility
module(DCOS_SERVERGROUP_CONFIGURE_WIZARD_ENVIRONMENTVARIABLES_CONTROLLER, []).controller(
  'dcosServerGroupEnvironmentVariablesController',
  [
    '$scope',
    function ($scope) {
      $scope.command.viewModel.env = [];

      this.isEnvironmentValid = function (env) {
        return !(typeof env === 'string' || env instanceof String);
      };

      // init from the model
      if ($scope.command.env && this.isEnvironmentValid($scope.command.env)) {
        Object.keys($scope.command.env).forEach((key) => {
          const val = $scope.command.env[key];
          let secretSource = null;
          if (val.secret) {
            secretSource = $scope.command.secrets[val.secret].source;
          }

          $scope.command.viewModel.env.push({
            name: key,
            value: val,
            rawValue: secretSource || val,
            isSecret: secretSource != null,
          });
        });
      }

      this.addEnvironmentVariable = function () {
        if (!this.isEnvironmentValid($scope.command.env)) {
          $scope.command.env = {};
        }

        $scope.command.viewModel.env.push({
          name: null,
          value: null,
          isSecret: false,
        });
      };

      this.removeEnvironmentVariable = function (index) {
        $scope.command.viewModel.env.splice(index, 1);
        this.synchronize();
      };

      this.updateValue = function (index) {
        if ($scope.command.viewModel.env[index].isSecret === true) {
          $scope.command.secrets['secret' + index].source = $scope.command.viewModel.env[index].rawValue;
        } else {
          $scope.command.viewModel.env[index].value = $scope.command.viewModel.env[index].rawValue;
        }
      };

      this.updateSecret = function (index, state) {
        // this is the previous state before the update is applied
        if (state !== true) {
          this.addSecret(index);
        } else {
          this.removeSecret(index);
        }
      };

      this.addSecret = function (index) {
        $scope.command.viewModel.env[index].rawValue = null;
        $scope.command.secrets['secret' + index] = {
          source: null,
        };

        $scope.command.viewModel.env[index].value = {
          secret: 'secret' + index,
        };
      };

      this.removeSecret = function (index) {
        $scope.command.viewModel.env[index].value = null;
        $scope.command.viewModel.env[index].rawValue = null;
        delete $scope.command.secrets['secret' + index];
      };

      this.synchronize = () => {
        const allNames = $scope.command.viewModel.env.map((item) => item.name);

        $scope.command.env = {};

        $scope.command.viewModel.env.forEach((item) => {
          if (item.name) {
            $scope.command.env[item.name] = item.value;
          }

          item.checkUnique = allNames.filter((name) => item.name !== name);
        });
      };
      $scope.$watch(() => JSON.stringify($scope.command.viewModel.env), this.synchronize);
    },
  ],
);

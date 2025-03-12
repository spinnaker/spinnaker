'use strict';

import { module } from 'angular';

export const DCOS_SERVERGROUP_CONFIGURE_WIZARD_OPTIONAL_CONTROLLER = 'spinnaker.dcos.serverGroup.configure.optional';
export const name = DCOS_SERVERGROUP_CONFIGURE_WIZARD_OPTIONAL_CONTROLLER; // for backwards compatibility
module(DCOS_SERVERGROUP_CONFIGURE_WIZARD_OPTIONAL_CONTROLLER, []).controller('dcosServerGroupOptionalController', [
  '$scope',
  function ($scope) {
    // Taken from jquery-validate plugin
    const urlRegex = /^(?:(?:(?:https?|ftp):)?\/\/)(?:\S+(?::\S*)?@)?(?:(?!(?:10|127)(?:\.\d{1,3}){3})(?!(?:169\.254|192\.168)(?:\.\d{1,3}){2})(?!172\.(?:1[6-9]|2\d|3[0-1])(?:\.\d{1,3}){2})(?:[1-9]\d?|1\d\d|2[01]\d|22[0-3])(?:\.(?:1?\d{1,2}|2[0-4]\d|25[0-5])){2}(?:\.(?:[1-9]\d?|1\d\d|2[0-4]\d|25[0-4]))|(?:(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)(?:\.(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)*(?:\.(?:[a-z\u00a1-\uffff]{2,})).?)(?::\d{2,5})?(?:[/?#]\S*)?(,)?$/;

    this.fetchPattern = {
      test: function (fetch) {
        if (fetch) {
          return fetch.split(',').every((item) => urlRegex.test(item));
        }

        return true;
      },
    };

    $scope.command.viewModel.fetch = null;

    // Init fetch from model
    if ($scope.command.fetch) {
      $scope.command.viewModel.fetch = $scope.command.fetch.map((fetchObj) => fetchObj['uri']).join(',');
    }

    this.synchronize = () => {
      if ($scope.command.viewModel.fetch) {
        $scope.command.fetch = $scope.command.viewModel.fetch.split(',').map((u) => {
          return { uri: u };
        });
      }
    };
    $scope.$watch(() => JSON.stringify($scope.command.viewModel.fetch), this.synchronize);
  },
]);

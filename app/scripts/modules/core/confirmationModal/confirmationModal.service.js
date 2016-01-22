'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.confirmationModal.service', [
    require('angular-ui-bootstrap'),
    require('./confirmationModal.controller.js'),
  ])
  .factory('confirmationModalService', function($uibModal, $sce) {
    var defaults = {
      buttonText: 'Confirm',
      cancelButtonText: 'Cancel'
    };

    function confirm(params) {
      params = angular.extend(angular.copy(defaults), params);

      if (params.body) {
        params.body = $sce.trustAsHtml(params.body);
      }

      var modalArgs = {
        templateUrl: require('./confirm.html'),
        controller: 'ConfirmationModalCtrl as ctrl',
        resolve: {
          params: function() {
            return params;
          }
        }
      };

      if (params.size) {
        modalArgs.size = params.size;
      }
      return $uibModal.open(modalArgs).result;
    }

    return {
      confirm: confirm
    };
  });

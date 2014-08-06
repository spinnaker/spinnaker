'use strict';

var angular = require('angular');

module.exports = function($modal) {
  var defaults = {
    buttonText: 'Confirm'
  };

  function confirm(params) {
    params = angular.extend(angular.copy(defaults), params);

    var modalArgs = {
      templateUrl: 'views/modal/confirm.html',
      controller: 'ConfirmationModalCtrl',
      resolve: {
        params: function() {
          return params;
        }
      }
    };

    if (params.size) {
      modalArgs.size = params.size;
    }
    return $modal.open(modalArgs).result;
  }

  return {
    confirm: confirm
  };
};

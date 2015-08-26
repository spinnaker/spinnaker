'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: require('../../views/application/modal/submitButton.html'),
    scope: {
      onClick: '&',
      isDisabled: '=',
      isNew: '=',
      submitting: '=',
      label: '=',
    }
  };
};

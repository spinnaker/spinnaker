'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: require('./submitButton.directive.html'),
    scope: {
      onClick: '&',
      isDisabled: '=',
      isNew: '=',
      submitting: '=',
      label: '=',
    }
  };
};

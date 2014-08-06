'use strict';

module.exports = function() {
  return {
    restrict: 'E',
    template: '<span class="label label-default account-label account-label-{{account}}">{{account}}</span>',
    scope: {
      account: '='
    }
  };
};

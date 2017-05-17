'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.oraclebmcs.footer.component', [
  ])
  .component('oracleBmcsFooter', {
    templateUrl: require('./footer.component.html'),
    bindings: {
      action: '&',
      isValid: '&',
      cancel: '&',
      account: '=?',
      verification: '=?',
    },
    controllerAs: 'vm',
    controller: angular.noop,
  });

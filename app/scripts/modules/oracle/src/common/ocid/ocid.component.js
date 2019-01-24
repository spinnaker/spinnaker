'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.oracle.ocid.component', [require('./truncateOcid.filter').name])
  .component('ocid', {
    templateUrl: require('./ocid.template.html'),
    bindings: {
      ocid: '=',
      showOcid: '@',
    },
  });

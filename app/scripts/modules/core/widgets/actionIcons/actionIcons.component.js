'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.actionIcons.component', [])
  .component('actionIcons', {
    bindings: {
      edit: '&',
      editInfo: '@',
      destroy: '&',
      destroyInfo: '@'
    },
    templateUrl: require('./actionIcons.component.html'),
    controller: angular.noop
  });

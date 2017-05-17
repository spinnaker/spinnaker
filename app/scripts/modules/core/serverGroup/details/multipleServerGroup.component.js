'use strict';

const angular = require('angular');

import './multipleServerGroup.component.less';

module.exports = angular
    .module('spinnaker.core.serverGroup.details.multipleServerGroup.component', [])
    .component('multipleServerGroup', {
      bindings: {
        serverGroup: '=',
      },
      templateUrl: require('./multipleServerGroup.component.html'),
    });

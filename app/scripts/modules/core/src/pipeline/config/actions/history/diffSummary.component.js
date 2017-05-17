'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.diffSummary.component', [])
  .component('diffSummary', {
    bindings: {
      summary: '='
    },
    controller: angular.noop,
    template: `
      <div class="diff-summary">
        <span class="footer-additions" ng-if="$ctrl.summary.additions">
          + {{$ctrl.summary.additions}}
        </span>
          <span class="footer-removals" ng-if="$ctrl.summary.removals">
          - {{$ctrl.summary.removals}}
        </span>
      </div>
`
  });

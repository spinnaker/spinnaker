'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.cacheBackedMultiSelectField.directive', [])
  .directive('osCacheBackedMultiSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: require('./cacheBackedMultiSelect.template.html'),
      scope: {
        cache: '=',
        refreshCache: '=',
        label: '@',
        model: '=',
        onChange: '&',
        required: '<?'
      },
      link: function(scope) {
        _.defaults(scope, {
          //wrap selectedOptions to work around issue with ng-model where binding only works if the model is a property of an object
          // (probably fixed in newer versions of AngularJS)
          state: { selectedOptions: scope.model },
          refreshTooltipLabel: scope.label,
          refreshTooltipTemplate: require('./cacheRefresh.tooltip.html'),
          cache: [],
          required: false,
          forceRefreshCache: _.isFunction(scope.refreshCache) ? scope.refreshCache : function() {},

          onSelectionsChanged: function() {
            //Hack to work around bug in ui-select where selected values re-appear in the drop-down
            scope.state.selectedOptions = _.uniq(scope.state.selectedOptions);
            scope.model = scope.state.selectedOptions;
            if( scope.onChange ) {
              var args = { selection: scope.model };
              scope.onChange(args);
            }
          }
        });

        function updateOptions() {
          scope.options = _.sortBy(scope.cache, 'name');
        }

        scope.$watch('cache', updateOptions);
        scope.$watch('model', function(model) {
          scope.state.selectedOptions = model;
        });
        updateOptions();
      }
    };
});

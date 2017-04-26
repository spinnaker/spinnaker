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
          // wrap selectedOptions to work around issue with ng-model where binding only works if the model is a property of an object
          // (probably fixed in newer versions of AngularJS)
          state: { selectedOptions: scope.model },
          refreshTooltipLabel: scope.label,
          refreshTooltipTemplate: require('./cacheRefresh.tooltip.html'),
          cache: [],
          required: false,
          forceRefreshCache: _.isFunction(scope.refreshCache) ? scope.refreshCache : function() {},

          onSelectionsChanged: function() {
            // Hack to work around bug in ui-select where selected values re-appear in the drop-down
            scope.state.selectedOptions = _.uniq(scope.state.selectedOptions);
          }
        });

        function updateOptions() {
          scope.options = _.sortBy(scope.cache, 'name');
        }

        // update the options whenever the associated cache gets updated
        scope.$watch('cache', updateOptions);

        // The following watchers are needed to overcome the fact that ui-select requires the model to be a child
        // property.  This code effectively removes that constraint for users of this directive.

        // carry through any changes from the UI to the provided model
        scope.$watch('state.selectedOptions', function() {
            // make sure the update is coming from the UI and not from the parent
            if ( scope.model !== scope.state.selectedOptions ) {
              scope.model = scope.state.selectedOptions;
              if ( scope.onChange ) {
                var args = { selection: scope.model };
                scope.onChange(args);
              }
            }
        });

        // make sure any changes in the model are reflected in the UI
        scope.$watch('model', function(model) {
          scope.state.selectedOptions = model;
        });

        updateOptions();
      }
    };
});

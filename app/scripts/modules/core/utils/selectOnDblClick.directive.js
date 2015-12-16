'use strict';

/** based on http://jsfiddle.net/epinapala/WdeTM/4/  **/
let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.selectOnDblClick', [])
  .directive('selectOnDblClick', function($window, $document) {
    return {
      restrict: 'A',
      link: function(scope, elem) {

        function selectText() {
          var selection = $window.getSelection();
          var range = $document[0].createRange();
          range.selectNodeContents(elem.get(0));
          selection.removeAllRanges();
          selection.addRange(range);
        }

        elem.bind('dblclick.textselection', selectText);

        scope.$on('$destroy', function() {
          elem.unbind('dblclick.textselection');
        });
      }
    };
  });

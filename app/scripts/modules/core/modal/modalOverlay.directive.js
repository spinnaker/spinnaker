'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.modal.modalOverlay.directive', [
])
  .directive('modalOverlay', function($timeout) {
    return {
      restrict: 'A',
      link: function(scope, elem) {
        $timeout(function() {
          var $uibModal = elem.closest('.modal-content'),
            modalHeight = $uibModal.height();

          if (modalHeight < 450) {
            modalHeight = 450;
          }

          $uibModal.height(modalHeight);
          elem.show().height(modalHeight).css({opacity: 1});

          scope.$on('$destroy', function() {
            elem.hide();
            elem.height(0).css({opacity: 0, scrollTop: 0});
            $uibModal.height('auto');
          });
        });
      }
    };
});

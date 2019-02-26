'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.modal.modalOverlay.directive', []).directive('modalOverlay', [
  '$timeout',
  function($timeout) {
    return {
      restrict: 'A',
      link: function(scope, elem) {
        $timeout(function() {
          var $uibModal = elem.closest('.modal-content'),
            modalHeight = $uibModal.outerHeight();

          if (modalHeight < 450) {
            modalHeight = 450;
          }

          $uibModal.height(modalHeight);
          elem
            .show()
            .height(modalHeight)
            .css({ opacity: 1 });

          let headerHeight = elem.find('.modal-header').outerHeight(),
            footerHeight = elem.find('.modal-footer').outerHeight();
          elem.find('.modal-body').css({ height: `calc(100% - ${headerHeight + footerHeight}px)` });

          scope.$on('$destroy', function() {
            elem.hide();
            elem.height(0).css({ opacity: 0, scrollTop: 0 });
            $uibModal.height('auto');
          });
        });
      },
    };
  },
]);

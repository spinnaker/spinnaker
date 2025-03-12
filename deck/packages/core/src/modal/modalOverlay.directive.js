'use strict';

import { module } from 'angular';

export const CORE_MODAL_MODALOVERLAY_DIRECTIVE = 'spinnaker.core.modal.modalOverlay.directive';
export const name = CORE_MODAL_MODALOVERLAY_DIRECTIVE; // for backwards compatibility
module(CORE_MODAL_MODALOVERLAY_DIRECTIVE, []).directive('modalOverlay', [
  '$timeout',
  function ($timeout) {
    return {
      restrict: 'A',
      link: function (scope, elem) {
        $timeout(function () {
          const $uibModal = elem.closest('.modal-content');
          let modalHeight = $uibModal.outerHeight();

          if (modalHeight < 450) {
            modalHeight = 450;
          }

          $uibModal.height(modalHeight);
          elem.show().height(modalHeight).css({ opacity: 1 });

          const headerHeight = elem.find('.modal-header').outerHeight();
          const footerHeight = elem.find('.modal-footer').outerHeight();
          elem.find('.modal-body').css({ height: `calc(100% - ${headerHeight + footerHeight}px)` });

          scope.$on('$destroy', function () {
            elem.hide();
            elem.height(0).css({ opacity: 0, scrollTop: 0 });
            $uibModal.height('auto');
          });
        });
      },
    };
  },
]);

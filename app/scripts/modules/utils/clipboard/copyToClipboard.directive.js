'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.copyToClipboard.directive', [])
  .directive('copyToClipboard', function($timeout) {
    return {
      restrict: 'E',
      scope: {},
      template: `
      <button class="btn btn-xs btn-link" aria-label="Copy to clipboard">
        <span
          class="clipboard-btn glyphicon glyphicon-copy"
          tooltip="{{cb.toolTip}}"
          tooltip-trigger="mouseenter"
          tooltip-placement="top"
          tooltip-enable="true"
          tooltip-is-open="cb.isOpen"
          data-clipboard-text="{{cb.text}}">
        </span>
      </button>`,

      bindToController: {
        text: '@',
        toolTip: '@'
      },
      controller: 'copyToClipboardController',
      controllerAs: 'cb',
      link: function(scope, element, attrs, controller) {
        element.on('click', () => {
          controller.isOpen = true;
          controller.toggleToolTipToCopied();
          scope.$digest();
          $timeout(() => {
            controller.isOpen = false;
            controller.resetToolTip();
            scope.$digest();
          }, 3000);
        });
      }
    };
  })
  .controller('copyToClipboardController', function() {
    let vm = this;
    vm.isOpen = false;
    vm.toggleToolTipToCopied = () => {
      vm.tempToolTip = vm.toolTip;
      vm.toolTip = 'Copied';
    };

    vm.resetToolTip = () => {
      vm.toolTip = vm.tempToolTip;
    };
  })
  .name;



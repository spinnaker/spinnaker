'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.forms.validateOnSubmit.directive', [])
  .directive('validateOnSubmit', function() {
    return {
      restrict: 'A',
      require: 'form',
      link: function(scope, element, attrs, formCtrl) {

        element.on('submit', function () {
          // Set controls to dirty so that validation formatting is applied
          var queue = [];
          var invalidCtrl = formCtrl;
          while (invalidCtrl) {
            invalidCtrl.$setDirty();

            angular.forEach(invalidCtrl.$error, function (invalidSubCtrls) {
              if (angular.isArray(invalidSubCtrls)) {
                // Add controls from sub-form
                queue.push.apply(queue, invalidSubCtrls);
              }
            });

            invalidCtrl = queue.shift();
          }

          // Click the form so that setDirty() takes immediate effect
          formCtrl.$$element.click();

          // Focus the first invalid element
          // NOTE: 'form.$error.required' doesn't list elements in document order, so we can't use that
          var firstInvalid = element.get(0).querySelector('.form-control.ng-invalid');
          if (firstInvalid) {
            firstInvalid.focus();

            if (firstInvalid.hasChildNodes()) {
              angular.forEach(firstInvalid.childNodes, function (child) {
                if (child.classList && child.classList.contains('ui-select-focusser')) {
                  child.focus();
                }
              });
            }
          }
        });
      }
    };
  });

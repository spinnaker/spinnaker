/* Directive to prevent form validation from propagating to the parent/enclosing form.
*/

'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.isolateForm.directive', [
])
  .directive('isolateForm', function () {
    return {
      restrict: 'A',
      require: '?form',
      link: function (scope, elm, attrs, ctrl) {
          if (!ctrl) {
              return;
          }

          // Get a copy of the controller (retain needed references)
          var ctrlCopy = angular.copy(ctrl);

          // Get the parent of the form
          var parent = elm.parent().controller('form');
          // Remove parent link to the controller
          parent.$removeControl(ctrl);

          // Replace form controller with a "isolated form"
          var isolatedFormCtrl = {
              $setValidity: function (validationToken, isValid, control) {
                  ctrlCopy.$setValidity(validationToken, isValid, control);
                  parent.$setValidity(validationToken, true, ctrl);
              },
              $setDirty: function () {
                  elm.removeClass('ng-pristine').addClass('ng-dirty');
                  ctrl.$dirty = true;
                  ctrl.$pristine = false;
              },
          };
          angular.extend(ctrl, isolatedFormCtrl);
      }
    };
  });

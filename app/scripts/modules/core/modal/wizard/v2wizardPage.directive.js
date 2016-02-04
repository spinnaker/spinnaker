'use strict';

/**
 * Wizard page directive
 * possible attributes:
 *   - key (required): Any string value, unique within the wizard; it becomes the the hook to access the page state
 *     through the wizard, e.g. wizard.getPage('page-1').markComplete()
 *   - label (required): Any string value; it becomes label in the wizard flow
 *   - done (optional, default: false): when set to true, the page will be marked complete when rendered
 *   - mandatory (optional, default: true): when set to false, the wizard will not consider this page when isComplete
 *     is called
 *   - render (optional, default: true): when set to false, registers the page with the wizard, but does not participate
 *     in the wizard flow. To add the page to the flow, call wizard.includePage(key)
 *   - markCompleteOnView (optional, default: true): when set to false, the page will not be marked complete when
 *     scrolled into view
 */
let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.modal.wizard.wizardPage.directive.v2', [
    require('./v2modalWizard.service.js'),
  ])
  .directive('v2WizardPage', function (v2modalWizardService) {
    return {
      restrict: 'E',
      transclude: true,
      scope: true,
      templateUrl: require('./v2wizardPage.directive.html'),
      link: function($scope, elem, attrs) {
        var state = {
          rendered: !attrs.render || $scope.$eval(attrs.render),
          done: attrs.done === 'true',
          dirty: false,
          required: attrs.mandatory !== 'false',
          markCompleteOnView: attrs.markCompleteOnView !== 'false',
        };
        $scope.key = attrs.key;
        $scope.label = attrs.label;
        $scope.state = state;
        v2modalWizardService.registerPage($scope.key, $scope.label, state);
      },
    };
  });


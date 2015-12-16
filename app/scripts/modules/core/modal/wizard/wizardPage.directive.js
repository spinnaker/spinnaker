/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


'use strict';

/**
 * Wizard page directive
 * possible attributes:
 *   - key (required): Any string value, unique within the wizard; it becomes the the hook to access the page state
 *     through the wizard, e.g. wizard.getPage('page-1').markComplete()
 *   - label (required): Any string value; it becomes label in the wizard flow
 *   - render (optional, default: true): when set to false, registers the page with the wizard, but does not participate
 *     in the wizard flow. To add the page to the flow, call wizard.includePage(key)
 *   - blocked (optional, default: true): when set to true, the page is immediately available by clicking on the link in
 *     the wizard flow
 *   - lazy (optional, default: false): when set to true, the page will actually be rendered and initialized but will
 *     not be displayed. This is useful if the page's controller should be initialized immediately
 */
let angular = require('angular');

module.exports = angular.module('spinnaker.core.modal.wizard.wizardPage.directive', [
])
  .directive('wizardPage', function () {
    return {
      restrict: 'E',
      transclude: true,
      require: '^modalWizard',
      scope: true,
      templateUrl: require('./wizardPage.directive.html'),
      link: function($scope, elem, attrs, wizardCtrl) {
        var state = {
          rendered: !attrs.render || $scope.$eval(attrs.render),
          done: attrs.done === 'true',
          dirty: false,
          blocked: attrs.blocked !== 'false',
          required: attrs.mandatory !== 'false'
        };
        $scope.hideSubheading = attrs.hideSubheading === 'true';
        $scope.key = attrs.key;
        $scope.label = attrs.label;
        $scope.lazy = attrs.lazy !== 'true';
        wizardCtrl.getWizard().registerPage($scope.key, $scope.label, state);
      }
    };
});


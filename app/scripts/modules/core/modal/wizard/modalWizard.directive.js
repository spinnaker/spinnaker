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

let angular = require('angular');

module.exports = angular.module('spinnaker.core.modalWizard', [
  require('./modalWizard.service.js'),
  require('./wizardPage.directive.js'),
])
  .directive('modalWizard', function () {
    return {
      restrict: 'E',
      transclude: true,
      templateUrl: require('./modalWizard.directive.html'),
      controller: 'ModalWizardCtrl as wizardCtrl',
      link: function(scope, elem, attrs) {
        scope.wizard.setHeading(attrs.heading);
        scope.wizard.hideIndicators = attrs.hideIndicators ? attrs.hideIndicators === 'true' : false;
        scope.showSubheadings = attrs.showSubheadings !== 'false';
      }
    };
  }
).controller('ModalWizardCtrl', function($scope, modalWizardService) {

    $scope.wizard  = modalWizardService.createWizard();

    this.getWizard = function() {
      return $scope.wizard;
    };

  }
);

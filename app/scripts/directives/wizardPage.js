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

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('wizardPage', function () {
    return {
      restrict: 'E',
      transclude: true,
      require: '^modalWizard',
      scope: true,
      templateUrl: 'views/modal/wizardPage.html',
      link: function($scope, elem, attrs, wizardCtrl) {
        var state = {
            rendered: attrs.render !== 'false',
            done: attrs.done === 'true',
            blocked: attrs.blocked !== 'false'
          };
        $scope.key = attrs.key;
        wizardCtrl.getWizard().registerPage($scope.key, state);
      }
    };
  }
);


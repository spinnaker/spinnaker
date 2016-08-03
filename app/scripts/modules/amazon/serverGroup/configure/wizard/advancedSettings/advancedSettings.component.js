/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.advancedSettings.component', [
    require('../../../../../core/modal/wizard/v2modalWizard.service.js'),
  ])
  .component('awsServerGroupAdvancedSettings', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./advancedSettings.component.html'),
    controller: function(v2modalWizardService) {
      this.fieldChanged = () => {
        if (this.command.keyPair) {
          v2modalWizardService.markComplete('advanced');
        }
      };
    }
  });

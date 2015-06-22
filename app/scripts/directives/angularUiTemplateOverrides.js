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

/**
 * Customizations to Angular UI templates
 */

/**
 * Allows us to use HTML in popover content. The only change is:
 *   ng-bind="content" => ng-bind-html="content"
 */

//BEN_TODO
module.exports = angular.module('spinnaker')
  .run(function($templateCache) {
    $templateCache.put('template/popover/popover.html',
        '<div class="popover {{placement}}" ng-class="{ in: isOpen(), fade: animation() }">\n' +
        '  <div class="arrow"></div>\n' +
        '\n' +
        '  <div class="popover-inner">\n' +
        '      <h3 class="popover-title" ng-bind="title" ng-show="title"></h3>\n' +
        '      <div class="popover-content" ng-bind-html="content"></div>\n' +
        '  </div>\n' +
        '</div>\n' +
        '');
  });

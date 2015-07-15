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
 * Customizations to Angular UI templates
 */

/**
 * Allows us to use HTML in popover content. The only change is:
 *   ng-bind="content" => ng-bind-html="content"
 */
angular.module('spinnaker')
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

    $templateCache.put('template/modal/backdrop.html',
      '<div class="modal-backdrop animate"\n' +
      '     modal-animation-class="fade"\n' +
      '     ng-class="{in: animate}"\n' +
      '></div>\n' +
      '');

    $templateCache.put('template/modal/window.html',
      '<div modal-render="{{$isRendered}}" tabindex="-1" role="dialog" class="modal"\n' +
      '    modal-animation-class="fade"\n' +
      '	ng-class=\"{in: animate}\" ng-style=\"{display: \'block\'}" ng-click="close($event)">\n' +
      '    <div class="modal-dialog" ng-class="size ? \'modal-\' + size : \'\'"><div class="modal-content" modal-transclude></div></div>\n' +
      '</div>\n' +
      '');
  });

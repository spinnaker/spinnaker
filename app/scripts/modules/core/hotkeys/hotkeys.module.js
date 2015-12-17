'use strict';

let angular = require('angular');

require('../../../../node_modules/angular-hotkeys/build/hotkeys.css');

module.exports = angular
  .module('spinnaker.hotkeys', [
    require('exports?"cfp.hotkeys"!angular-hotkeys'),
  ])
  .config(function(hotkeysProvider) {
    hotkeysProvider.template =
      `<div class="cfp-hotkeys-container fade" ng-class="{in: helpVisible}" style="display: none;"><div class="cfp-hotkeys">
                      <h4 class="cfp-hotkeys-title" ng-if="!header">{{ title }}</h4>
                      <div ng-bind-html="header" ng-if="header"></div>
                      <div style="display:flex; flex-direction:row; align-items: flex-start; justify-content: center">
                        <div>
                           <table>
                             <tbody>
                              <tr>
                                <td class="cfp-hotkeys-keys">
                                  <span class="cfp-hotkeys-key">/</span>
                                </td>
                                <td>Global Search</td>
                              </tr>
                              <tr ng-repeat="hotkey in hotkeys | filter:{ description: \'!$$undefined$$\', combo: \'+shift+\'}">
                                <td class="cfp-hotkeys-keys">
                                  <span ng-repeat="key in hotkey.format() track by $index" class="cfp-hotkeys-key">{{ key }}</span>
                                </td>
                                <td class="cfp-hotkeys-text">{{ hotkey.description }}</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                        <div>
                          <table>
                            <tbody>
                              <tr ng-repeat="hotkey in hotkeys | filter:{ description: \'!$$undefined$$\', combo: \'+alt+\'}">
                                <td class="cfp-hotkeys-keys">
                                  <span ng-repeat="key in hotkey.format() track by $index" class="cfp-hotkeys-key">{{ key }}</span>
                                </td>
                                <td class="cfp-hotkeys-text">{{ hotkey.description }}</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      </div>
                      <div ng-bind-html="footer" ng-if="footer"></div>
                      <div class="cfp-hotkeys-close" ng-click="toggleCheatSheet()">×</div>
                    </div></div>`;
  })
  .run(function($state, hotkeys) {
    let globalHotkeys = [
      {
        combo: 'ctrl+shift+a',
        description: "Applications",
        callback: () => $state.go('home.applications'),
      },
      {
        combo: 'ctrl+shift+i',
        description: "Infrastructure",
        callback: () => $state.go('home.infrastructure'),
      },
      {
        combo: 'ctrl+shift+d',
        description: 'Data',
        callback: () => $state.go('home.data'),
      },

    ];

    globalHotkeys.forEach((hotkeyConfig) => hotkeys.add(hotkeyConfig));
  });

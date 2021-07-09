import * as angular from 'angular';

import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE } from './tagManager.service';
import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTOR_COMPONENT } from './tagSelector.component';

('use strict');

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTORGENERATOR_COMPONENT =
  'spinnaker.deck.gce.tagSelectorGenerator.component';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTORGENERATOR_COMPONENT; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTORGENERATOR_COMPONENT, [
    GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTOR_COMPONENT,
    GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE,
  ])
  .directive('gceTagSelectorGenerator', [
    '$compile',
    'gceTagManager',
    function ($compile, gceTagManager) {
      const template = `<gce-tag-selector security-group-id="securityGroupId" command="command">
                    </gce-tag-selector>`;

      return {
        restrict: 'E',
        scope: {
          command: '=',
          securityGroupId: '=',
        },
        link: function (scope, element) {
          const { securityGroupId } = scope;
          const securityGroupObject = gceTagManager.securityGroupObjectsKeyedById[securityGroupId];

          if (securityGroupObject && securityGroupObject.tagsArray.length < 2) {
            securityGroupObject.tagsArray.forEach((tagName) => gceTagManager.addTag(tagName));
            return;
          }

          const compiledTemplate = $compile(template)(scope);

          angular.element(element).closest('.ui-select-match-item').after(compiledTemplate);
        },
      };
    },
  ]);

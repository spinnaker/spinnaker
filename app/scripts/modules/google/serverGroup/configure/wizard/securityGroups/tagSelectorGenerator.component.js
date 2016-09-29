'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.tagSelectorGenerator.component', [
  require('./tagSelector.component.js'),
  require('./tagManager.service.js')
])
  .directive('gceTagSelectorGenerator', function($compile, gceTagManager) {
    let template = `<gce-tag-selector security-group-id="securityGroupId" command="command">
                    </gce-tag-selector>`;

    return {
      restrict: 'E',
      scope: {
        command: '=',
        securityGroupId: '=',
      },
      link: function(scope, element) {
        let { securityGroupId } = scope;
        let securityGroupObject = gceTagManager.securityGroupObjectsKeyedById[securityGroupId];

        if (securityGroupObject.tagsArray.length < 2) {
          securityGroupObject.tagsArray.forEach((tagName) => gceTagManager.addTag(tagName));
          return;
        }

        let compiledTemplate = $compile(template)(scope);

        angular
          .element(element)
          .closest('.ui-select-match-item')
          .after(compiledTemplate);
      }
    };
  });

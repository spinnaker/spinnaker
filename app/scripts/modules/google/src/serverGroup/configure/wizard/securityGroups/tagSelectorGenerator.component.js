'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.deck.gce.tagSelectorGenerator.component', [
    require('./tagSelector.component').name,
    require('./tagManager.service').name,
  ])
  .directive('gceTagSelectorGenerator', function($compile, gceTagManager) {
    const template = `<gce-tag-selector security-group-id="securityGroupId" command="command">
                    </gce-tag-selector>`;

    return {
      restrict: 'E',
      scope: {
        command: '=',
        securityGroupId: '=',
      },
      link: function(scope, element) {
        const { securityGroupId } = scope;
        const securityGroupObject = gceTagManager.securityGroupObjectsKeyedById[securityGroupId];

        if (securityGroupObject && securityGroupObject.tagsArray.length < 2) {
          securityGroupObject.tagsArray.forEach(tagName => gceTagManager.addTag(tagName));
          return;
        }

        const compiledTemplate = $compile(template)(scope);

        angular
          .element(element)
          .closest('.ui-select-match-item')
          .after(compiledTemplate);
      },
    };
  });

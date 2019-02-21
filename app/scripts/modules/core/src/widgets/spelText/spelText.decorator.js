'use strict';

const angular = require('angular');
require('jquery-textcomplete');

import './spel.less';

let decorateFn = function($delegate, spelAutocomplete) {
  let directive = $delegate[0];

  let link = directive.link.pre;

  directive.compile = function() {
    return function(scope, el) {
      link.apply(this, arguments);

      let type = el.attr('type');
      if (type === 'checkbox' || type === 'radio' || type === 'search' || el.closest('.no-spel').length) {
        return;
      }

      // the textcomplete plugin needs input texts to marked as 'contenteditable'
      el.attr('contenteditable', true);
      spelAutocomplete.addPipelineInfo(scope.pipeline).then(textcompleteConfig => {
        el.textcomplete &&
          el.textcomplete(textcompleteConfig, {
            maxCount: 1000,
            zIndex: 5000,
            dropdownClassName: 'dropdown-menu textcomplete-dropdown spel-dropdown',
          });
      });

      function listener(evt) {
        let hasSpelPrefix = evt.target.value.includes('$');
        let parent = el.parent();
        let hasLink = parent && parent.nextAll && parent.nextAll('.spelLink');

        if (hasSpelPrefix) {
          if (hasLink.length < 1) {
            // Add the link to the docs under the input/textarea
            el.parent().after(
              '<a class="spelLink" href="http://spinnaker.github.io/guides/user/pipeline-expressions" target="_blank">Expression Docs</a>',
            );

            el.addClass('monospace');
          }
        } else {
          el.removeClass('monospace');
          hasLink.fadeOut(500, function() {
            this.remove();
          });
        }
      }

      el.bind('keyup', listener);
    };
  };

  return $delegate;
}
decorateFn.$inject = ['$delegate', 'spelAutocomplete'];;

module.exports = angular
  .module('spinnaker.core.widget.spelText', [require('./spelAutocomplete.service').name])
  .config(['$provide', function($provide) {
    $provide.decorator('inputDirective', decorateFn);
    $provide.decorator('textareaDirective', decorateFn);
  }]);

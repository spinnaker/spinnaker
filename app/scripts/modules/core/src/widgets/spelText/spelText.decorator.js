'use strict';

import { module } from 'angular';

import { CORE_WIDGETS_SPELTEXT_SPELAUTOCOMPLETE_SERVICE } from './spelAutocomplete.service';

import './spel.less';

require('jquery-textcomplete');

decorateFn.$inject = ['$delegate', 'spelAutocomplete'];
function decorateFn($delegate, spelAutocomplete) {
  const directive = $delegate[0];

  const link = directive.link.pre;

  directive.compile = function () {
    return function (scope, el) {
      link.apply(this, arguments);

      const type = el.attr('type');
      if (type === 'checkbox' || type === 'radio' || type === 'search' || el.closest('.no-spel').length) {
        return;
      }

      // the textcomplete plugin needs input texts to marked as 'contenteditable'
      el.attr('contenteditable', true);
      spelAutocomplete.addPipelineInfo(scope.pipeline).then((textcompleteConfig) => {
        el.textcomplete &&
          el.textcomplete(textcompleteConfig, {
            maxCount: 1000,
            zIndex: 5000,
            dropdownClassName: 'dropdown-menu textcomplete-dropdown spel-dropdown',
          });
      });

      function listener(evt) {
        if ($(evt.target).hasClass('no-doc-link')) {
          return;
        }

        const hasSpelPrefix = evt.target.value.includes('$');
        const parent = el.parent();
        const hasLink = parent && parent.nextAll && parent.nextAll('.spelLink');

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
          hasLink.fadeOut(500, function () {
            this.remove();
          });
        }
      }

      el.bind('keyup', listener);
    };
  };

  return $delegate;
}

export const CORE_WIDGETS_SPELTEXT_SPELTEXT_DECORATOR = 'spinnaker.core.widget.spelText';
export const name = CORE_WIDGETS_SPELTEXT_SPELTEXT_DECORATOR; // for backwards compatibility
module(CORE_WIDGETS_SPELTEXT_SPELTEXT_DECORATOR, [CORE_WIDGETS_SPELTEXT_SPELAUTOCOMPLETE_SERVICE]).config([
  '$provide',
  function ($provide) {
    $provide.decorator('inputDirective', decorateFn);
    $provide.decorator('textareaDirective', decorateFn);
  },
]);

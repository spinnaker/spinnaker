'use strict';

let angular = require('angular');
require('./spel.less');
require('jquery-textcomplete');

let decorateFn = function ($delegate, jsonListBuilder, spelAutocomplete) {
  let directive = $delegate[0];

  let link = directive.link.pre;

  directive.compile = function () {
    return function (scope, el) {

      link.apply(this, arguments);

      // the textcomplete plugin needs input texts to marked as 'contenteditable'
      el.attr('contenteditable', true);
      spelAutocomplete.addPipelineInfo(scope.pipeline).then((textcompleteConfig) => {
        el.textcomplete(textcompleteConfig, {
          maxCount: 1000,
          zIndex: 5000,
          dropdownClassName: 'dropdown-menu textcomplete-dropdown spel-dropdown'
        });
      });

      function listener (evt) {

        let hasSpelPrefix = evt.target.value.indexOf('$') > -1;
        let hasLink = el.parent().nextAll('.spelLink');


        if (hasSpelPrefix) {
          if (hasLink.length < 1) {
            // Add the link to the docs under the input/textarea
            el.parent().after('<a class="spelLink" href="http://www.spinnaker.io/docs/pipeline-expressions-guide" target="_blank">Expression Docs</a>');


          }
        } else {
          hasLink.fadeOut( 500, function() { this.remove(); });
        }
      }

      el.bind('keyup', listener);
    };
  };

  return $delegate;
};


module.exports = angular
  .module('spinnaker.core.widget.spelText', [
    require('./spelAutocomplete.service'),
    require('./jsonListBuilder'),
  ])
  .config( function($provide) {
    $provide.decorator('inputDirective', decorateFn);
    $provide.decorator('textareaDirective', decorateFn);
  });



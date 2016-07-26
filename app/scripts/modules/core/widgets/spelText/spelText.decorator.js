'use strict';

let angular = require('angular');
require('./spel.less');

let decorateFn = function ($delegate) {
  var directive = $delegate[0];

  var link = directive.link.pre;

  directive.compile = function () {
    return function (scope, el) {

      link.apply(this, arguments);
      function listener (evt) {
        let hasSpelPrefix = evt.target.value.indexOf('${') > -1;
        let hasLink = el.parent().nextAll('.spelLink');
        if (hasSpelPrefix) {
          if (hasLink.length < 1) {
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
  .module('spinnaker.core.widget.spelText', [])
  .config( function($provide) {
    $provide.decorator('inputDirective', decorateFn);
    $provide.decorator('textareaDirective', decorateFn);
  });



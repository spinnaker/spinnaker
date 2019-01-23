'use strict';

module.exports = function($provide) {
  // The GCP nested approach throws an exception when activating the select when there's a nested select option,
  // but it's harmless so we trap it and discard it

  $provide.decorator('uiSelectMinErr', function($delegate) {
    return function handledError() {
      var original = $delegate;
      if (arguments.length === 3) {
        if (
          arguments[0] === 'choices' &&
          arguments[1] === `Expected multiple .ui-select-choices-row but got '{0}'.` &&
          arguments[2] === 0
        ) {
          throw new Error('IGNORE', 'IGNORE');
        }
      }
      return original.apply(this, arguments);
    };
  });

  $provide.decorator('$exceptionHandler', function($delegate) {
    return function(exception, cause) {
      if (exception && exception.message === 'IGNORE') {
        return;
      }

      if (Array.isArray(exception) && exception.length && exception[0] instanceof Error) {
        exception.forEach(e => $delegate(e, cause));
      } else {
        $delegate(exception, cause);
      }
    };
  });
};

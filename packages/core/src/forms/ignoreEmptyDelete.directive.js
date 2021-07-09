import { module } from 'angular';
import UI_SELECT from 'ui-select';

('use strict');

export const CORE_FORMS_IGNOREEMPTYDELETE_DIRECTIVE = 'spinnaker.core.utils.ignoreEmptyDelete.directive';
export const name = CORE_FORMS_IGNOREEMPTYDELETE_DIRECTIVE; // for backwards compatibility
module(CORE_FORMS_IGNOREEMPTYDELETE_DIRECTIVE, [UI_SELECT]).config([
  '$provide',
  function ($provide) {
    $provide.decorator('uiSelectMultipleDirective', [
      '$delegate',
      function ($delegate) {
        // because we hacked the multiple select directive CSS so drastically,
        // when the focus is in the search field in multiselect mode, pressing delete
        // behaves unexpectedly out of the box: it will delete previous selections,
        // then navigate the browser back a page.
        // This fix is only nominally better in that it prevents that behavior most of the time.
        // If the user repeatedly presses delete quickly, or holds down delete, all bets are off.
        // Still, it's better than nothing.
        const directive = $delegate[0];
        const originalLink = directive.link;
        directive.compile = function () {
          return function (scope, elem, attrs, ctrls) {
            originalLink.apply(this, arguments);
            const $select = ctrls[0];
            function ignoreDelete(e) {
              const key = e.which;
              if (key === 8 || key === 46) {
                if (!$select.searchInput.val().length) {
                  e.preventDefault();
                  e.stopPropagation();
                  if (e.originalEvent) {
                    e.originalEvent.preventDefault();
                    e.originalEvent.stopPropagation();
                  }
                  $select.activate(false, true);
                }
              }
            }
            const events = 'keydown.ignoreEmptyDelete keyup.ignoreEmptyDelete';
            $select.searchInput.on(events, ignoreDelete);
            if ($select.focusser) {
              $select.focusser.on(events, ignoreDelete);
            }
          };
        };
        return $delegate;
      },
    ]);
  },
]);

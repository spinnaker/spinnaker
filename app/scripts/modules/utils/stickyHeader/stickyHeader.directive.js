'use strict';

/**
 * Based on https://github.com/polarblau/stickySectionHeaders
 */

angular.module('deckApp.utils.stickyHeader', [
  'deckApp.utils.jQuery',
  'deckApp.utils.lodash',
])
  .directive('stickyHeader', function ($log, $window, _, $) {
    return {
      restrict: 'A',
      link: {
        post: function (scope, elem, attrs) {
          var $heading = elem,
            $section = $heading.parent(),
            $scrollableContainer = $heading.closest('[sticky-headers]'),
            id = parseInt(Math.random() * new Date().getTime());

          if (!$scrollableContainer.length) {
            $log.warn('No parent container with attribute "sticky-header"; headers will not stick.');
            return;
          }

          $scrollableContainer.css({position: 'relative'});

          var addedOffsetHeight = attrs.addedOffsetHeight ? parseInt(attrs.addedOffsetHeight) : 0,
              addedOffsetWidth = attrs.addedOffsetWidth ? parseInt(attrs.addedOffsetWidth) : 0;

          var positionHeader = _.throttle(function () {

            var sectionRect = $section.get(0).getBoundingClientRect(),
              sectionTop = sectionRect.bottom - sectionRect.height,
              windowHeight = $window.innerHeight,
              bottom = sectionRect.bottom;

            if (sectionRect.bottom < 0 || sectionTop > windowHeight) {
              clearStickiness($section, $heading);
              return;
            }

            var containerTop = $scrollableContainer.offset().top,
                top = sectionTop - containerTop - addedOffsetHeight;

            if (top < 0 && bottom > containerTop + addedOffsetHeight) {
              var containerWidth = $scrollableContainer.width(),
                  headingHeight = $heading.outerHeight();
              var topBase = containerTop,
                  zIndex = 3;
              if (containerTop + headingHeight + addedOffsetHeight > bottom) {
                topBase = bottom - headingHeight - addedOffsetHeight;
                zIndex = 2;
              }
              $section.css({
                paddingTop: headingHeight,
              });
              $heading.addClass('heading-sticky').css({
                top: topBase + addedOffsetHeight,
                width: containerWidth + addedOffsetWidth,
                zIndex: zIndex
              });
            } else {
              clearStickiness($section, $heading);
            }

          }, 50);

          function destroyStickyBindings() {
            $scrollableContainer.unbind('.stickyHeader-' + id);
            $($window).unbind('.stickyHeader-' + id);
          }

          function clearStickiness($section, $heading) {
            $section.css({
              paddingTop: 0,
            });
            if ($heading.get(0).className.indexOf('heading-sticky') !== -1) {
              $heading.removeClass('heading-sticky').addClass('not-sticky');
            }
          }

          function toggleSticky(enabled) {
            if (enabled) {
              $scrollableContainer.bind('scroll.stickyHeader-' + id + ' resize.stickyHeader-' + id, positionHeader);
              $($window).bind('resize.stickyHeader-' + id, positionHeader);

              scope.$on('$destroy', destroyStickyBindings);
            } else {
              destroyStickyBindings();
            }
          }

          if (attrs.stickyIf) {
            scope.$watch(attrs.stickyIf, toggleSticky);
          } else {
            toggleSticky(true);
          }

        },
      }
    };
  });

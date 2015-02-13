'use strict';

/**
 * Based on https://github.com/polarblau/stickySectionHeaders
 */

angular.module('deckApp.utils.stickyHeader', [
  'deckApp.utils.jQuery',
])
  .directive('stickyHeader', function ($log, _) {
    return {
      restrict: 'A',
      link: {
        post: function (scope, elem) {
          var $heading = elem,
            $section = $heading.parent(),
            $scrollableContainer = $heading.closest('[sticky-headers]'),
            id = parseInt(Math.random() * new Date().getTime());

          if (!$scrollableContainer.length) {
            $log.warn('No parent container with attribute "sticky-header"; headers will not stick.');
            return;
          }

          $scrollableContainer.css({position: 'relative'});

          var positionHeader = _.throttle(function () {
            var containerTop = $scrollableContainer.offset().top,
              containerWidth = $scrollableContainer.width(),
              sectionOffset = $section.offset(),
              top = sectionOffset.top - containerTop;

            if (top < 0) {
              $section.css({
                paddingTop: $heading.height(),
              });
              $heading.addClass('heading-sticky').css({
                top: containerTop,
                width: containerWidth,
              });
            } else {
              $section.css({
                paddingTop: 0,
              });
              $heading.removeClass('heading-sticky').css({
                top: '',
                width: '',
                zIndex: 3,
              });
            }

          }, 100);

          $scrollableContainer.bind('scroll.stickyHeader-' + id, positionHeader);
          $scrollableContainer.bind('resize.stickyHeader-' + id, positionHeader);

          scope.$on('$destroy', function () {
            $scrollableContainer.unbind('.stickyHeader-' + id);
          });
        },
      }
    };
  });

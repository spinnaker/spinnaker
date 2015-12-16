'use strict';

/**
 * Based on https://github.com/polarblau/stickySectionHeaders
 */

require('./stickyHeader.less');

let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.stickyHeader', [
  require('../jQuery.js'),
  require('../lodash.js'),
])
  .directive('stickyHeader', function ($log, $window, _, $) {
    return {
      restrict: 'A',
      link: {
        post: function (scope, elem, attrs) {
          var $heading = elem,
            $section = $heading.parent(),
            $scrollableContainer = $heading.closest('[sticky-headers]'),
            id = parseInt(Math.random() * new Date().getTime()),
            isSticky = false;

          if (!$scrollableContainer.length) {
            $log.warn('No parent container with attribute "sticky-header"; headers will not stick.');
            return;
          }

          $scrollableContainer.css({position: 'relative'});

          var addedOffsetHeight = attrs.addedOffsetHeight ? parseInt(attrs.addedOffsetHeight) : 0;
          var positionHeader = _.throttle(function () {

            var sectionRect = $section.get(0).getBoundingClientRect(),
              sectionTop = sectionRect.bottom - sectionRect.height,
              windowHeight = $window.innerHeight,
              bottom = sectionRect.bottom;

            if (sectionRect.bottom < 0 || sectionTop > windowHeight) {
              clearStickiness($section);
              return;
            }

            var containerTop = $scrollableContainer.offset().top,
                top = sectionTop - containerTop - addedOffsetHeight;

            if (top < 0 && bottom > containerTop + addedOffsetHeight) {
              var headingRect = $heading.get(0).getBoundingClientRect(),
                  headingWidth = headingRect.width,
                  headingHeight = headingRect.height;
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
                width: headingWidth,
                zIndex: zIndex
              });
              isSticky = true;
            } else {
              clearStickiness($section);
            }

          }, 50);

          function resetHeaderWidth() {
            if ($heading.get(0).className.indexOf('heading-sticky') !== -1) {
              $heading.removeClass('heading-sticky').addClass('not-sticky').css({width: '' });
            }
          }

          function handleWindowResize() {
            resetHeaderWidth();
            positionHeader();
          }

          function destroyStickyBindings() {
            $scrollableContainer.unbind('.stickyHeader-' + id);
            $($window).unbind('.stickyHeader-' + id);
            $section.removeData();
            $heading.removeData();
          }

          function clearStickiness($section) {
            if (isSticky) {
              $section.css({
                paddingTop: 0,
              });
              resetHeaderWidth();
            }
            isSticky = false;
          }

          function toggleSticky(enabled) {
            if (enabled) {
              $scrollableContainer.bind('scroll.stickyHeader-' + id + ' resize.stickyHeader-' + id, positionHeader);
              $($window).bind('resize.stickyHeader-' + id, handleWindowResize);

              scope.$on('page-reflow', handleWindowResize);

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

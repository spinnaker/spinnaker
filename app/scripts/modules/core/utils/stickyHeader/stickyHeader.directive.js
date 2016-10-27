'use strict';

/**
 * Based on https://github.com/polarblau/stickySectionHeaders
 */

require('./stickyHeader.less');

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.stickyHeader', [
  require('../jQuery.js'),
])
  .directive('stickyHeader', function ($log, $window, $) {
    return {
      restrict: 'A',
      link: {
        post: function (scope, elem, attrs) {
          var $heading = elem,
            $section = $heading.parent(),
            $scrollableContainer = $heading.closest('[sticky-headers]'),
            id = parseInt(Math.random() * new Date().getTime()),
            isSticky = false,
            notifyOnly = attrs.notifyOnly === 'true';

          if (!$scrollableContainer.length) {
            $log.warn('No parent container with attribute "sticky-header"; headers will not stick.');
            return;
          }

          if (!notifyOnly) {
            $scrollableContainer.css({position: 'relative'});
          }

          var addedOffsetHeight = attrs.addedOffsetHeight ? parseInt(attrs.addedOffsetHeight) : 0;
          var positionHeader = _.throttle(function () {

            var sectionRect = $section.get(0).getBoundingClientRect(),
              sectionTop = sectionRect.top,
              windowHeight = $window.innerHeight,
              bottom = sectionRect.bottom;

            if (bottom < 0 || sectionTop > windowHeight) {
              clearStickiness($section);
              return;
            }

            var containerTop = $scrollableContainer.offset().top,
                top = sectionTop - containerTop;

            if (top < 0 && bottom > containerTop + addedOffsetHeight) {
              var headingRect = $heading.get(0).getBoundingClientRect(),
                  headingWidth = headingRect.width,
                  headingHeight = $heading.outerHeight(true);
              var topBase = containerTop + addedOffsetHeight,
                  zIndex = 3;
              if (containerTop + headingHeight > bottom) {
                topBase = bottom - headingHeight + addedOffsetHeight;
                zIndex = 2;
              }
              let newHeaderStyle = {
                top: topBase,
                width: headingWidth,
                zIndex: zIndex
              };
              if (notifyOnly) {
                scope.$emit('sticky-header-enabled', newHeaderStyle);
              } else {
                $section.css({
                  paddingTop: headingHeight,
                });
                $heading.addClass('heading-sticky').css(newHeaderStyle);
              }
              isSticky = true;
            } else {
              clearStickiness($section);
            }

          }, 100);

          function resetHeaderWidth() {
            if ($heading.get(0).className.includes('heading-sticky')) {
              $heading.removeClass('heading-sticky').addClass('not-sticky').css({width: '', top: '' });
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
              if (notifyOnly) {
                scope.$emit('sticky-header-disabled');
              } else {
                $section.css({
                  paddingTop: 0,
                });
                resetHeaderWidth();
              }
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

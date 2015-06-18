// This is a modded version of the original directive
'use strict';


var mod;

mod = angular.module('infinite-scroll', []);

mod.directive('infiniteScroll', ['$rootScope', '$window', '$timeout', function($rootScope, $window, $timeout) {
  return {
    link: function(scope, elem, attrs) {
      var checkWhenEnabled, handler, scrollDistance, scrollEnabled;
      $window = angular.element($window);
      elem.css('overflow-y', 'auto');
      elem.css('overflow-x', 'hidden');
      elem.css('height', 'inherit');
      scrollDistance = 0;
      if (attrs.infiniteScrollDistance != null) {
        scope.$watch(attrs.infiniteScrollDistance, function(value) {
          return (scrollDistance = parseInt(value, 10));
        });
      }
      scrollEnabled = true;
      checkWhenEnabled = false;
      if (attrs.infiniteScrollDisabled != null) {
        scope.$watch(attrs.infiniteScrollDisabled, function(value) {
          scrollEnabled = !value;
          if (scrollEnabled && checkWhenEnabled) {
            checkWhenEnabled = false;
            return handler();
          }
        });
      }
      $rootScope.$on('refreshStart', function(){
        elem.animate({ scrollTop: "0" });
      });
      handler = function() {
        var container, elementBottom, remaining, shouldScroll, containerBottom;
        container = $(elem.children()[0]);
        elementBottom = elem.offset().top + elem.height();
        containerBottom = container.offset().top + container.height();
        remaining = containerBottom - elementBottom ;
        shouldScroll = remaining <= elem.height() * scrollDistance;
        if (shouldScroll && scrollEnabled) {
          if ($rootScope.$$phase) {
            return scope.$eval(attrs.infiniteScroll);
          } else {
            return scope.$apply(attrs.infiniteScroll);
          }
        } else if (shouldScroll) {
          return (checkWhenEnabled = true);
        }
      };
      elem.on('scroll', handler);
      scope.$on('$destroy', function() {
        return $window.off('scroll', handler);
      });
      return $timeout((function() {
        if (attrs.infiniteScrollImmediateCheck) {
          if (scope.$eval(attrs.infiniteScrollImmediateCheck)) {
            return handler();
          }
        } else {
          return handler();
        }
      }), 0);
    }
  };
}
]);


///* ng-infinite-scroll - v1.2.0 - 2014-12-02 */
//var mod;
//
//mod = angular.module('infinite-scroll', []);
//
//mod.value('THROTTLE_MILLISECONDS', null);
//
//mod.directive('infiniteScroll', [
//  '$rootScope', '$window', '$interval', 'THROTTLE_MILLISECONDS', function($rootScope, $window, $interval, THROTTLE_MILLISECONDS) {
//    return {
//      scope: {
//        infiniteScroll: '&',
//        infiniteScrollContainer: '=',
//        infiniteScrollDistance: '=',
//        infiniteScrollDisabled: '=',
//        infiniteScrollUseDocumentBottom: '='
//      },
//      link: function(scope, elem, attrs) {
//        var changeContainer, checkWhenEnabled, container, handleInfiniteScrollContainer, handleInfiniteScrollDisabled, handleInfiniteScrollDistance, handleInfiniteScrollUseDocumentBottom, handler, height, immediateCheck, offsetTop, pageYOffset, scrollDistance, scrollEnabled, throttle, useDocumentBottom, windowElement;
//        windowElement = angular.element($window);
//        scrollDistance = null;
//        scrollEnabled = null;
//        checkWhenEnabled = null;
//        container = null;
//        immediateCheck = true;
//        useDocumentBottom = false;
//        height = function(elem) {
//          elem = elem[0] || elem;
//          if (isNaN(elem.offsetHeight)) {
//            return elem.document.documentElement.clientHeight;
//          } else {
//            return elem.offsetHeight;
//          }
//        };
//        offsetTop = function(elem) {
//          if (!elem[0].getBoundingClientRect || elem.css('none')) {
//            return;
//          }
//          return elem[0].getBoundingClientRect().top + pageYOffset(elem);
//        };
//        pageYOffset = function(elem) {
//          elem = elem[0] || elem;
//          if (isNaN(window.pageYOffset)) {
//            return elem.document.documentElement.scrollTop;
//          } else {
//            return elem.ownerDocument.defaultView.pageYOffset;
//          }
//        };
//        handler = function() {
//          var containerBottom, containerTopOffset, elementBottom, remaining, shouldScroll;
//          if (container === windowElement) {
//            containerBottom = height(container) + pageYOffset(container[0].document.documentElement);
//            elementBottom = offsetTop(elem) + height(elem);
//          } else {
//            containerBottom = height(container);
//            containerTopOffset = 0;
//            if (offsetTop(container) !== void 0) {
//              containerTopOffset = offsetTop(container);
//            }
//            elementBottom = offsetTop(elem) - containerTopOffset + height(elem);
//          }
//          if (useDocumentBottom) {
//            elementBottom = height((elem[0].ownerDocument || elem[0].document).documentElement);
//          }
//          remaining = elementBottom - containerBottom;
//          shouldScroll = remaining <= height(container) * scrollDistance + 1;
//          if (shouldScroll) {
//            checkWhenEnabled = true;
//            if (scrollEnabled) {
//              if (scope.$$phase || $rootScope.$$phase) {
//                return scope.infiniteScroll();
//              } else {
//                return scope.$apply(scope.infiniteScroll);
//              }
//            }
//          } else {
//            return checkWhenEnabled = false;
//          }
//        };
//        throttle = function(func, wait) {
//          var later, previous, timeout;
//          timeout = null;
//          previous = 0;
//          later = function() {
//            var context;
//            previous = new Date().getTime();
//            $interval.cancel(timeout);
//            timeout = null;
//            func.call();
//            return context = null;
//          };
//          return function() {
//            var now, remaining;
//            now = new Date().getTime();
//            remaining = wait - (now - previous);
//            if (remaining <= 0) {
//              clearTimeout(timeout);
//              $interval.cancel(timeout);
//              timeout = null;
//              previous = now;
//              return func.call();
//            } else {
//              if (!timeout) {
//                return timeout = $interval(later, remaining, 1);
//              }
//            }
//          };
//        };
//        if (THROTTLE_MILLISECONDS != null) {
//          handler = throttle(handler, THROTTLE_MILLISECONDS);
//        }
//        scope.$on('$destroy', function() {
//          return container.unbind('scroll', handler);
//        });
//        handleInfiniteScrollDistance = function(v) {
//          return scrollDistance = parseFloat(v) || 0;
//        };
//        scope.$watch('infiniteScrollDistance', handleInfiniteScrollDistance);
//        handleInfiniteScrollDistance(scope.infiniteScrollDistance);
//        handleInfiniteScrollDisabled = function(v) {
//          scrollEnabled = !v;
//          if (scrollEnabled && checkWhenEnabled) {
//            checkWhenEnabled = false;
//            return handler();
//          }
//        };
//        scope.$watch('infiniteScrollDisabled', handleInfiniteScrollDisabled);
//        handleInfiniteScrollDisabled(scope.infiniteScrollDisabled);
//        handleInfiniteScrollUseDocumentBottom = function(v) {
//          return useDocumentBottom = v;
//        };
//        scope.$watch('infiniteScrollUseDocumentBottom', handleInfiniteScrollUseDocumentBottom);
//        handleInfiniteScrollUseDocumentBottom(scope.infiniteScrollUseDocumentBottom);
//        changeContainer = function(newContainer) {
//          if (container != null) {
//            container.unbind('scroll', handler);
//          }
//          container = newContainer;
//          if (newContainer != null) {
//            return container.bind('scroll', handler);
//          }
//        };
//        changeContainer(windowElement);
//        handleInfiniteScrollContainer = function(newContainer) {
//          if ((newContainer == null) || newContainer.length === 0) {
//            return;
//          }
//          if (newContainer instanceof HTMLElement) {
//            newContainer = angular.element(newContainer);
//          } else if (typeof newContainer.append === 'function') {
//            newContainer = angular.element(newContainer[newContainer.length - 1]);
//          } else if (typeof newContainer === 'string') {
//            newContainer = angular.element(document.querySelector(newContainer));
//          }
//          if (newContainer != null) {
//            return changeContainer(newContainer);
//          } else {
//            throw new Exception("invalid infinite-scroll-container attribute.");
//          }
//        };
//        scope.$watch('infiniteScrollContainer', handleInfiniteScrollContainer);
//        handleInfiniteScrollContainer(scope.infiniteScrollContainer || []);
//        if (attrs.infiniteScrollParent != null) {
//          changeContainer(angular.element(elem.parent()));
//        }
//        if (attrs.infiniteScrollImmediateCheck != null) {
//          immediateCheck = scope.$eval(attrs.infiniteScrollImmediateCheck);
//        }
//        return $interval((function() {
//          if (immediateCheck) {
//            return handler();
//          }
//        }), 0, 1);
//      }
//    };
//  }
//]);

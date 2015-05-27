'use strict';


angular.module('spinnaker.notifications')
  .directive('notifications', function ($exceptionHandler, $timeout) {
    return {
      scope: {},
      restrict: 'E',
      replace: true,
      templateUrl: 'scripts/modules/notifications/notifications.html',
      controller: function ($scope, notificationsService) {

        var addNotificationProps = function (notificationsObj) {
          return Object.defineProperties(notificationsObj, {
            unread: {
              get: function () {
                return this.filter(function (notification) {
                  return !notification.$read;
                }).length;
              },
            },
            hasUnread: {
              get: function () {
                return this.unread.length > 0;
              },
            },
            hasActive: {
              get: function () {
                return this.active.length > 0;
              },
            },
            onView: {
              value: function () {
                this.forEach(function (notification) {
                  notification.$read = true;
                  notification.$ephemeral = false;
                });
              },
            },
            active: {
              get: function () {
                return addNotificationProps(this.filter(function (notification) {
                  return !notification.$dismissed;
                }));
              },
            },
            hasEphemeral: {
              get: function() {
                return this.some(function(notification) {
                  return notification.$ephemeral;
                });
              },
            },
            ephemeral: {
              get: function() {
                return this.filter(function(notification) {
                  return notification.$ephemeral && !notification.$dismissed;
                });
              },
            },
          });
        };

        $scope.notifications = addNotificationProps([]);

        $scope.showEphemeral = function() {

          if ($scope.notifications.hasEphemeral) {
            return !$scope.open;
          }
          return false;
        };

        notificationsService.subscribe(function (notification) {
          Object.defineProperties(notification, {
            $ephemeral: {
              value: true,
              writable: true,
            },
            $read: {
              value: false,
              writable: true,
            },
            $dismissed: {
              value: false,
              writable: true,
            },
            dismiss: {
              value: function ($event) {
                if ($event && $event.stopPropagation) {
                  $event.stopPropagation();
                }
                this.$dismissed = true;
              },
            },
          });

          $scope.notifications.push(notification);

          $scope.$evalAsync(function() {
            $timeout(function() {
              notification.$ephemeral = false;
              if (notification.autoDismiss) {
                notification.dismiss();
              }
            }, 5000);
          });
        }, function (err) {
          $exceptionHandler(err);
        });
      },
    };
  }
);

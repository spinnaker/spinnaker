'use strict';

let angular = require('angular');

require('./notificationList.directive.html');
require('./modal/editNotification.html');

module.exports = angular.module('spinnaker.core.notifications.notificationList', [])
    .directive('notificationList', function () {
        return {
            restrict: 'E',
            scope: {
                application: '=',
                level: '@',
                notifications: '=',
                parent: '='
            },
            templateUrl: require('./notificationList.directive.html'),
            controller: 'NotificationListCtrl',
            controllerAs: 'notificationListCtrl'
        };
    })
    .controller('NotificationListCtrl', function ($scope, $uibModal, notificationService, _) {

        var vm = this;

        vm.revertNotificationChanges = function () {

            /*
                we currently store application level notifications in front50 as an map indexed by type
                {
                     "application": "ayuda",
                     "hipchat": [ { ... } ],
                     "email": [ { ... } ]
                }
                the code below unwraps it into a table friendly format and the saveNotifications code will
                write it back into the right format.

                We will change the format in front50 when we rewrite notifications to use CQL so this tranformation
                is no longer needed
             */

            notificationService.getNotificationsForApplication($scope.application).then(function (notifications) {
                $scope.notifications = _.filter(_.flatten(_.map(['email', 'sms', 'hipchat', 'slack'],
                    function (type) {
                        if (notifications[type]) {
                            return _.map(notifications[type], function (entry) {
                                    return _.extend(entry, {type: type});
                                }
                            );
                        }
                    }
                )), function (allow) {
                    return allow !== undefined && allow.level === 'application';
                });
                vm.isNotificationsDirty = false;
            });
        };

        if ($scope.level === 'application') {
            vm.revertNotificationChanges();
        }

        vm.saveNotifications = function () {
            var toSaveNotifications = {};
            toSaveNotifications.application = $scope.application;

            _.each($scope.notifications, function (notification) {
                if (toSaveNotifications[notification.type] === undefined) {
                    toSaveNotifications[notification.type] = [];
                }
                toSaveNotifications[notification.type].push(notification);
            });

            notificationService.saveNotificationsForApplication($scope.application, toSaveNotifications).then(function () {
                vm.revertNotificationChanges();
            });

        };

        vm.editNotification = function (notification) {
            var modalInstance = $uibModal.open({
                templateUrl: require('./modal/editNotification.html'),
                controller: 'EditNotificationController',
                controllerAs: 'editNotification',
                resolve: {
                    notification: function () {
                        return notification;
                    },
                    level : function() {
                        return $scope.level;
                    }
                }
            });

            modalInstance.result.then(function (newNotification) {
                if (!notification) {
                    $scope.notifications.push(newNotification);
                } else {
                    $scope.notifications[$scope.notifications.indexOf(notification)] = newNotification;
                }
                vm.isNotificationsDirty = true;
            });

        };

        vm.addNotification = function () {
            if ($scope.parent && !$scope.parent.notifications) {
                $scope.parent.notifications = [];
            }
            vm.editNotification(undefined);
        };

        vm.removeNotification = function (notification) {
            $scope.notifications = $scope.notifications.filter(function (el) {
                    return el !== notification;
                }
            );
            vm.isNotificationsDirty = true;
        };

        return vm;

    });

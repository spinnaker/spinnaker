'use strict';

module.exports = function () {
  return {
    template: require('views/sorttoggle.html'),
    scope: {
      key: '@',
      label: '@',
      onChange: '&',
      sortModel: '=',
    },
    restrict: 'A',
    controller: function($scope) {
      var ctrl = this;

      this.isSortKey = function (key) {
        var field = $scope.sortModel.key;
        return field === key || field === '-' + key;
      };

      this.isReverse = function() {
        return $scope.sortModel.key.indexOf('-') === 0;
      };

      this.setSortKey = function (key, $event) {
        $event.preventDefault();
        var predicate = ctrl.isSortKey(key) && ctrl.isReverse() ? '' : '-';
        $scope.sortModel.key = predicate + key;
        if ($scope.onChange) {
          $scope.onChange();
        }
      };
    },
  };
};

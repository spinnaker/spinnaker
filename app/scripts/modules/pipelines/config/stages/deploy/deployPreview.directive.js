angular.module('deckApp.pipelines')
  .directive('deployPreview', function(serverGroupService) {
    return {
      restrict: 'E',
      scope: {
        stage: '='
      },
      templateUrl: 'scripts/modules/pipelines/config/stages/deploy/deployPreview.html',
      link: function(scope) {

        function buildClusterName() {
          var cluster = scope.stage.cluster;
          scope.clusterName = serverGroupService.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
        }

        function buildDisplayableRegions() {
          if (scope.stage.cluster && scope.stage.cluster.availabilityZones) {
            var availabilityZones = scope.stage.cluster.availabilityZones,
              result = [];
            if (availabilityZones) {
              var regions = Object.keys(availabilityZones);
              result = regions.map(function (region) {
                return { region: region, availabilityZones: availabilityZones[region] };
              });
            }
            scope.regionsWithZones = result;
          }
        }

        scope.$watch('stage.cluster.availabilityZones', buildDisplayableRegions);
        scope.$watch('stage.cluster', buildClusterName);
      }
    }
  });

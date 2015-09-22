'use strict';

let angular = require('angular');

require('./project.less');

module.exports = angular.module('spinnaker.core.projects.project.controller', [
  require('./configure/configureProject.modal.controller.js'),
  require('./service/project.read.service.js'),
  require('../history/recentHistory.service.js'),
])
  .controller('ProjectCtrl', function ($scope, $modal, projectReader, $state, projectConfiguration, recentHistoryService) {

    $scope.project = projectConfiguration;

    recentHistoryService.addExtraDataToLatest('projects',
      {
        config: {
          applications: projectConfiguration.config.applications
        }
      });

    projectConfiguration.config.applications = projectConfiguration.config.applications || [];

    let selectedApplication = null;

    // $stateParams is scoped to parent state, so if an application is selected, it will not be visible
    $scope.$on('$stateChangeSuccess', function(event, toState, toParams) {
      selectedApplication = toParams.application;
      if (selectedApplication) {
        $scope.viewState.navSelection = $scope.navOptions.find(option => option.title === selectedApplication);
      }
      $scope.viewState.dashboard = !selectedApplication;
    });

    $scope.viewState = {
      projectLoaded: false,
    };

    $scope.navOptions = [
      {
        title: 'Project Dashboard',
        view: 'home.project.dashboard',
        params: { project: projectConfiguration.name }
      }
    ];

    projectConfiguration.config.applications.sort().forEach((application) => $scope.navOptions.push(
      {
        title: application,
        view: 'home.project.application.insight.clusters',
        params: { application: application, project: projectConfiguration.name}
      }
    ));

    if (selectedApplication) {
      $scope.viewState.navSelection = $scope.navOptions.find(option => option.title === selectedApplication);
    } else {
      $scope.viewState.navSelection = $scope.navOptions[0];
    }
    $scope.viewState.dashboard = !selectedApplication;

    this.navigate = () => {
      var selection = $scope.viewState.navSelection;
      $state.go(selection.view, selection.params);
    };

    this.configureProject = () => {
      $modal.open({
        templateUrl: require('./configure/configureProject.modal.html'),
        controller: 'ConfigureProjectModalCtrl',
        controllerAs: 'ctrl',
        resolve: {
          projectConfig: () => projectConfiguration,
        },
      }).result.then((result) => {
          if (result.action === 'delete') {
            $state.go('home.infrastructure');
          }
          if (result.action === 'upsert') {
            if (result.name !== projectConfiguration.name) {
              $state.go($state.current, {project: result.name}, {location: 'replace'});
            } else {
              $state.go($state.current, {}, {reload: true});
            }
          }
      });
    };

  }).name;

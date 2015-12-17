'use strict';

let angular = require('angular');

require('./project.less');

module.exports = angular.module('spinnaker.core.projects.project.controller', [
  require('./configure/configureProject.modal.controller.js'),
  require('../utils/lodash.js'),
])
  .controller('ProjectCtrl', function ($scope, $uibModal, $timeout, $state, projectConfiguration, _) {

    $scope.project = projectConfiguration;

    projectConfiguration.config.applications = projectConfiguration.config.applications || [];

    let selectedApplication = null;

    // $stateParams is scoped to parent state, so if an application is selected, it will not be visible
    $scope.$on('$stateChangeSuccess', function(event, toState, toParams) {
      selectedApplication = toParams.application;
      if (selectedApplication) {
        $scope.viewState.navSelection = _.find($scope.navOptions, (option) => option.title === selectedApplication);
      }
      $scope.viewState.dashboard = !selectedApplication;
    });

    $scope.viewState = {
      projectLoaded: false,
      showMenu: false,
      navigating: false,
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
      $scope.viewState.navSelection = _.find($scope.navOptions, (option) => option.title === selectedApplication);
    } else {
      $scope.viewState.navSelection = $scope.navOptions[0];
    }
    $scope.viewState.dashboard = !selectedApplication;

    this.hideNavigationMenu = () => {
      // give the navigate method a chance to fire before hiding the menu
      $timeout(() => {
        if (!$scope.viewState.navigating) {
          $scope.viewState.showMenu = false;
        }
      }, 100 );
    };

    this.navigate = (option) => {
      $scope.viewState.navSelection = option;
      $scope.viewState.showMenu = false;
      $state.go(option.view, option.params);
      $scope.viewState.navigating = false;
    };

    this.configureProject = () => {
      $uibModal.open({
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

  });

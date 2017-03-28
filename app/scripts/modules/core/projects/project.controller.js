'use strict';

import _ from 'lodash';


let angular = require('angular');

require('./project.less');

module.exports = angular.module('spinnaker.core.projects.project.controller', [
  require('./configure/configureProject.modal.controller.js'),
])
  .controller('ProjectCtrl', function ($scope, $uibModal, $timeout, $state, projectConfiguration) {

    this.project = projectConfiguration;

    this.viewState = {
      projectLoaded: false,
      showMenu: false,
      navigating: false,
    };

    this.navOptions = [
      {
        title: 'Project Dashboard',
        view: 'home.project.dashboard',
        params: { project: projectConfiguration.name }
      }
    ];

    let initialize = () => {
      projectConfiguration.config.applications = projectConfiguration.config.applications || [];

      let selectedApplication = null;

      // $stateParams is scoped to parent state, so if an application is selected, it will not be visible
      $scope.$on('$stateChangeSuccess', (event, toState, toParams) => {
        selectedApplication = toParams.application;
        if (selectedApplication) {
          this.viewState.navSelection = _.find(this.navOptions, (option) => option.title === selectedApplication);
        }
        this.viewState.dashboard = !selectedApplication;
      });

      projectConfiguration.config.applications.sort().forEach((application) => this.navOptions.push(
        {
          title: application,
          view: 'home.project.application.insight.clusters',
          type: 'application',
          params: { application: application, project: projectConfiguration.name}
        }
      ));

      if (selectedApplication) {
        this.viewState.navSelection = _.find(this.navOptions, (option) => option.title === selectedApplication);
      } else {
        this.viewState.navSelection = this.navOptions[0];
      }
      this.viewState.dashboard = !selectedApplication;
    };

    if (projectConfiguration.config) {
      initialize();
    }

    this.hideNavigationMenu = () => {
      // give the navigate method a chance to fire before hiding the menu
      $timeout(() => {
        if (!this.viewState.navigating) {
          this.viewState.showMenu = false;
        }
      }, 100 );
    };

    this.navigate = (option) => {
      this.viewState.navSelection = option;
      this.viewState.showMenu = false;
      $state.go(option.view, option.params);
      this.viewState.navigating = false;
    };

    this.configureProject = () => {
      $uibModal.open({
        templateUrl: require('./configure/configureProject.modal.html'),
        controller: 'ConfigureProjectModalCtrl',
        controllerAs: 'ctrl',
        size: 'lg',
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

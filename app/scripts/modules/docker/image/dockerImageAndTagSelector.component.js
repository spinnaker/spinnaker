'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.docker.imageAndTagSelector.component', [
    require('./image.reader.js'),
  ])
  .component('dockerImageAndTagSelector', {
    bindings: {
      specifyTagByRegex: '=',
      organization: '=',
      registry: '=',
      repository: '=',
      tag: '=',
      account: '='
    },
    templateUrl: require('./dockerImageAndTagSelector.component.html'),
    controller: function ($scope, dockerImageReader) {
      $scope.viewState = {
        imagesLoaded: false,
        imagesRefreshing: false,
      };

      let updateOrganizationsList = () => {
        if (!$scope.accountMap) {
          return;
        }

        this.registry = $scope.registryMap[this.account];
        $scope.organizations = $scope.accountMap[this.account] || [];
        if ($scope.organizations.indexOf(this.organization) < 0) {
          this.organization = null;
        }
        updateRepositoryList();
      };

      let updateRepositoryList = () => {
        if (!$scope.organizationMap) {
          return;
        }
        let key = `${this.account}/${this.organization || '' }`;
        $scope.repositories = $scope.organizationMap[key] || [];
        if ($scope.repositories.indexOf(this.repository) < 0) {
          this.repository = null;
        }
        updateTag();
      };

      let updateTag;
      if (this.specifyTagByRegex) {
        updateTag = () => {
          if (_.trim(this.tag) === '') {
            this.tag = null;
          }
        };
      } else {
        updateTag = () => {
          if (!$scope.repositoryMap) {
            return;
          }
          let key = this.repository;
          $scope.tags = $scope.repositoryMap[key] || [];
          if ($scope.tags.indexOf(this.tag) < 0) {
            this.tag = null;
          }
        };
      }

      let initializeImages = () => {
        dockerImageReader.findImages({ provider: 'dockerRegistry' }).then((images) => {
          $scope.images = images;
          $scope.registryMap = images.reduce((map, image) => {
            map[image.account] = image.registry;
            return map;
          }, {});
          $scope.accountMap = images.reduce((map, image) => {
            let key = image.account;
            if (!key) {
              return map;
            }
            let all = map[key] || [];
            let parts = image.repository.split('/');
            parts.pop();
            let org = parts.join('/');
            if (all.indexOf(org) < 0) {
              map[key] = all.concat(org);
            }
            return map;
          }, {});
          $scope.accounts = Object.keys($scope.accountMap);
          $scope.organizationMap = images.reduce((map, image) => {
            if (!image.repository) {
              return map;
            }
            let parts = image.repository.split('/');
            parts.pop();
            let key = `${image.account}/${parts.join('/')}`;
            let all = map[key] || [];
            if (all.indexOf(image.repository) < 0) {
              map[key] = all.concat(image.repository);
            }
            return map;
          }, {});
          $scope.organizations = Object.keys($scope.organizationMap);
          updateOrganizationsList();

          $scope.repositoryMap = images.reduce((map, image) => {
            if (!image.repository) {
              return map;
            }

            let key = image.repository;
            let all = map[key] || [];
            if (all.indexOf(image.tag) < 0) {
              map[key] = all.concat(image.tag);
            }

            return map;
          }, {});
          updateTag();

          $scope.viewState.imagesLoaded = true;
          $scope.viewState.imagesRefreshing = false;
        });
      };

      this.refreshImages = () => {
        $scope.viewState.imagesRefreshing = true;
        initializeImages();
      };

      initializeImages();

      $scope.$watch('$ctrl.account', updateOrganizationsList);
      $scope.$watch('$ctrl.organization', updateRepositoryList);
      $scope.$watch('$ctrl.repository', updateTag);
      $scope.$watch('$ctrl.tag', updateTag);

    }
  });

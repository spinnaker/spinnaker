/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';


angular.module('deckApp.modalWizard', [])
  .directive('modalWizard', function () {
    return {
      restrict: 'E',
      transclude: true,
      templateUrl: 'views/modal/modalWizard.html',
      controller: 'ModalWizardCtrl as wizardCtrl',
      link: function(scope, elem, attrs) {
        scope.wizard.setHeading(attrs.heading);
        scope.wizard.hideIndicators = attrs.hideIndicators ? attrs.hideIndicators === 'true' : false;
        scope.showSubheadings = attrs.showSubheadings !== 'false';
      }
    };
  }
).controller('ModalWizardCtrl', function($scope, modalWizardService) {

    $scope.wizard  = modalWizardService.createWizard();

    this.getWizard = function() {
      return $scope.wizard;
    };

  }
).factory('modalWizardService', function(_) {
    var modalWizard;

    function createWizard() {
      var wizard = {
        renderedPages: [],
        pageRegistry: [],
        currentPage: null
      };

      wizard.setHeading = function(heading) {
        wizard.heading = heading;
      };

      wizard.markDirty = function(pageKey) {
        wizard.getPage(pageKey).state.dirty = true;
      };

      wizard.markClean = function(pageKey) {
        wizard.getPage(pageKey).state.dirty = false;
      };

      wizard.markComplete = function(pageKey) {
        wizard.getPage(pageKey).state.done = true;
      };

      wizard.markIncomplete = function(pageKey) {
        wizard.getPage(pageKey).state.done = false;
      };

      wizard.markInaccessible = function(pageKey) {
        wizard.getPage(pageKey).state.blocked = true;
      };

      wizard.markAccessible = function(pageKey) {
        wizard.getPage(pageKey).state.blocked = false;
      };

      wizard.setCurrentPage = function(page) {
        wizard.pageRegistry.forEach(function(test) { test.state.current = test === page; });
        wizard.currentPage = page;
      };

      wizard.getPage = function(pageKey) {
        return _.find(wizard.pageRegistry, {key: pageKey});
      };

      wizard.getPageIndex = function(pageKey) {
        return _.findIndex(wizard.renderedPages, {key: pageKey});
      };

      wizard.getCurrentPageIndex = function() {
        return wizard.renderedPages.indexOf(wizard.currentPage);
      };

      wizard.registerPage = function(pageKey, label, state) {
        state = state || { done: false, blocked: true, rendered: true, current: false };
        wizard.pageRegistry.push({key: pageKey, label: label, state: state});
        wizard.renderPages();
      };

      wizard.renderPages = function() {
        var renderedPages = _.filter(wizard.pageRegistry, function(page) { return page.state.rendered; });
        wizard.renderedPages = renderedPages;
        if (renderedPages.length === 1) {
          wizard.setCurrentPage(renderedPages[0]);
        }
      };

      wizard.nextPage = function (markComplete) {
        var currentPageIndex = wizard.getCurrentPageIndex();
        if (currentPageIndex === wizard.renderedPages.length - 1) {
          return;
        }
        if (markComplete) {
          wizard.markComplete(wizard.currentPage.key);
        }
        wizard.setCurrentPage(wizard.renderedPages[currentPageIndex + 1]);
        wizard.direction = 'forward';
        wizard.jump = '';
      };

      wizard.previousPage = function (markComplete) {
        var currentPageIndex = wizard.getCurrentPageIndex();
        if (currentPageIndex < 1) {
          return;
        }
        if (markComplete) {
          wizard.markComplete(wizard.currentPage.key);
        }
        wizard.setCurrentPage(wizard.renderedPages[currentPageIndex - 1]);
        wizard.direction = 'back';
        wizard.jump = '';
      };

      wizard.jumpToPage = function (page) {
        var jumpIndex = wizard.getPageIndex(page),
          currentPageIndex = wizard.getCurrentPageIndex();

        if (jumpIndex === -1 || jumpIndex === currentPageIndex) {
          return;
        }

        var jump = Math.abs(jumpIndex - currentPageIndex) > 1 ? 'jump' : '',
          direction = jumpIndex > currentPageIndex ? 'forward' : 'back';

        wizard.direction = direction;
        wizard.jump = jump;
        wizard.setCurrentPage(wizard.getPage(page));
      };

      wizard.isComplete = function () {
        return _(wizard.renderedPages)
          .collect('state')
          .filter({rendered: true, required: true})
          .every({done: true, dirty: false});
      };

      wizard.allPagesVisited = function () {
        return _(wizard.renderedPages)
          .collect('state')
          .filter({rendered: true, required: true})
          .every({done: true});
      };

      wizard.isFirstPage = function(key) {
        return wizard.renderedPages.length && wizard.renderedPages[0].key === key;
      };

      wizard.isLastPage = function(key) {
        return wizard.renderedPages.length && wizard.renderedPages[wizard.renderedPages.length-1].key === key;
      };

      function setRendered(pageKey, rendered) {
        _.forEach(_.filter(wizard.pageRegistry, 'key', pageKey), function(page) { page.state.rendered = rendered; });
        wizard.renderPages();
      }

      wizard.includePage = function(pageKey) {
        setRendered(pageKey, true);
      };

      wizard.excludePage = function(pageKey) {
        setRendered(pageKey, false);
      };

      modalWizard = wizard;

      return wizard;
    }

    function deleteWizard() {
      modalWizard = null;
    }

    function getWizard() {
      return modalWizard;
    }

    return {
      createWizard: createWizard,
      deleteWizard: deleteWizard,
      getWizard: getWizard
    };
  }
);


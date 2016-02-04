'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.modalWizard.service.v2', [
    require('../../utils/lodash.js'),
    require('../../utils/scrollTo/scrollTo.service.js'),
  ])
  .factory('v2modalWizardService', function(_, scrollToService) {

    this.renderedPages = [];
    this.pageRegistry = [];
    this.currentPage = null;
    this.heading = null;

    this.setHeading = (heading) => this.heading = heading;

    this.getPage = (pageKey) => {
      return _.find(this.pageRegistry, {key: pageKey});
    };

    this.markDirty = (pageKey) => this.getPage(pageKey).state.dirty = true;

    this.markClean = (pageKey) => this.getPage(pageKey).state.dirty = false;

    this.markComplete = (pageKey) => this.getPage(pageKey).state.done = true;

    this.markIncomplete = (pageKey) => this.getPage(pageKey).state.done = false;

    this.setCurrentPage = (page, skipScroll) => {
      this.pageRegistry.forEach((test) => test.state.current = (test === page));
      this.currentPage = page;
      this.markClean(page.key);

      if (page.state.markCompleteOnView) {
        this.markComplete(page.key);
      }
      if (!skipScroll) {
        scrollToService.scrollTo(`[waypoint="${page.key}"]`, '[waypoint-container]', 115);
      }
    };

    this.registerPage = (pageKey, label, state) => {
      state = state || { done: false, blocked: true, rendered: true, current: false };
      this.pageRegistry.push({key: pageKey, label: label, state: state});
      this.renderPages();
    };

    this.renderPages = () => {
      var renderedPages = this.pageRegistry.filter((page) => page.state.rendered);
      this.renderedPages = renderedPages;
      if (renderedPages.length === 1) {
        this.setCurrentPage(renderedPages[0]);
      }
    };

    this.isComplete = () => _(this.renderedPages)
      .collect('state')
      .filter({rendered: true, required: true})
      .every({done: true, dirty: false});

    this.allPagesVisited = () => _(this.renderedPages)
      .collect('state')
      .filter({rendered: true, required: true})
      .every({done: true});

    this.setRendered = (pageKey, rendered) => {
      this.pageRegistry.filter((page) => page.key === pageKey)
        .forEach((page) => page.state.rendered = rendered);
      this.renderPages();
    };

    this.includePage = (pageKey) => this.setRendered(pageKey, true);
    this.excludePage = (pageKey) => this.setRendered(pageKey, false);

    this.resetWizard = () => {
      this.renderedPages.length = 0;
      this.pageRegistry.length = 0;
      this.currentPage = null;
      this.heading = null;
    };

    return this;
  });

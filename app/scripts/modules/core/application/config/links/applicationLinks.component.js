'use strict';

require('./applicationLinks.component.less');

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.applicationLinks.component', [
    require('../../../utils/lodash'),
    require('../../service/applications.write.service'),
    require('./editLinks.modal.controller'),
    require('../../../config/settings'),
    require('angular-ui-bootstrap'),
  ])
  .component('applicationLinks', {
    bindings: {
      application: '=',
    },
    templateUrl: require('./applicationLinks.component.html'),
    controller: function($uibModal, applicationWriter, settings, _) {

      let initialize = () => {
        if (this.application.notFound) {
          return;
        }

        this.sections = _.cloneDeep(this.application.attributes.instanceLinks || settings.defaultInstanceLinks || []);

        this.viewState = {
          originalSections: _.cloneDeep(this.sections),
          originalStringVal: JSON.stringify(this.sections),
          saving: false,
          saveError: false,
          isDirty: false,
        };

        this.setDefaultLinkState();
      };

      this.setDefaultLinkState = () => {
        this.usingDefaultLinks = angular.toJson(this.sections) === angular.toJson(settings.defaultInstanceLinks);
        this.defaultLinksConfigured = !!settings.defaultInstanceLinks;
      };

      this.revert = initialize;

      this.useDefaultLinks = () => {
        this.sections = _.cloneDeep(settings.defaultInstanceLinks);
        this.configChanged();
      };

      this.save = () => {
        this.viewState.saving = true;
        this.viewState.saveError = false;
        applicationWriter.updateApplication({
          name: this.application.name,
          accounts: this.application.attributes.accounts,
          instanceLinks: angular.copy(this.sections),
        })
          .then(() => {
            let sections = _.cloneDeep(angular.copy(this.sections));
            this.application.attributes.instanceLinks = sections;
            initialize();
          }, () => {
            this.viewState.saving = false;
            this.viewState.saveError = true;
          });
      };

      this.addLink = (section) => {
        section.links.push({title: '', path: ''});
        this.configChanged();
      };

      this.removeLink = (section, index) => {
        section.links.splice(index, 1);
        this.configChanged();
      };

      this.addSection = () => {
        let section = {title: '', links: []};
        this.sections.push(section);
        this.addLink(section);
      };

      this.removeSection = (index) => {
        this.sections.splice(index, 1);
        this.configChanged();
      };

      this.configChanged = () => {
        this.setDefaultLinkState();
        this.viewState.isDirty = this.viewState.originalStringVal !== JSON.stringify(angular.copy(this.sections));
      };

      this.editJson = () => {
        $uibModal.open({
          templateUrl: require('./editLinks.modal.html'),
          controller: 'EditLinksModalCtrl as vm',
          resolve: {
            sections: () => this.sections,
          }
        }).result.then(newSections => {
          this.sections = newSections;
          this.configChanged();
        });
      };

      this.sortOptions = {
        axis: 'y',
        delay: 150,
        stop: () => this.configChanged(),
      };

      this.$onInit = initialize;
    }
  });

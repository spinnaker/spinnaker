'use strict';

const angular = require('angular');
import _ from 'lodash';

import { SETTINGS } from 'core/config/settings';
import { CONFIG_SECTION_FOOTER } from '../footer/configSectionFooter.component';

import './applicationLinks.component.less';

module.exports = angular
  .module('spinnaker.core.application.config.applicationLinks.component', [
    require('./editLinks.modal.controller').name,
    require('angular-ui-bootstrap'),
    CONFIG_SECTION_FOOTER,
  ])
  .component('applicationLinks', {
    bindings: {
      application: '=',
    },
    templateUrl: require('./applicationLinks.component.html'),
    controller: function($uibModal) {
      let initialize = () => {
        if (this.application.notFound) {
          return;
        }

        this.sections = _.cloneDeep(this.application.attributes.instanceLinks || SETTINGS.defaultInstanceLinks || []);

        this.viewState = {
          originalSections: _.cloneDeep(this.sections),
          originalStringVal: JSON.stringify(this.sections),
          saving: false,
          saveError: false,
          isDirty: false,
        };

        this.cloudProviders = this.application.attributes.cloudProviders
          ? this.application.attributes.cloudProviders
          : [];

        this.setDefaultLinkState();
      };

      this.setDefaultLinkState = () => {
        this.usingDefaultLinks = angular.toJson(this.sections) === angular.toJson(SETTINGS.defaultInstanceLinks);
        this.defaultLinksConfigured = !!SETTINGS.defaultInstanceLinks;
      };

      this.revert = initialize;

      this.useDefaultLinks = () => {
        this.sections = _.cloneDeep(SETTINGS.defaultInstanceLinks);
        this.configChanged();
      };

      this.addLink = section => {
        section.links.push({ title: '', path: '' });
        this.configChanged();
      };

      this.removeLink = (section, index) => {
        section.links.splice(index, 1);
        this.configChanged();
      };

      this.addSection = () => {
        let section = { title: '', links: [] };
        this.sections.push(section);
        this.addLink(section);
      };

      this.removeSection = index => {
        this.sections.splice(index, 1);
        this.configChanged();
      };

      this.configChanged = () => {
        this.setDefaultLinkState();
        this.viewState.isDirty = this.viewState.originalStringVal !== JSON.stringify(angular.copy(this.sections));
      };

      this.editJson = () => {
        $uibModal
          .open({
            templateUrl: require('./editLinks.modal.html'),
            controller: 'EditLinksModalCtrl as vm',
            size: 'lg modal-fullscreen',
            resolve: {
              sections: () => this.sections,
            },
          })
          .result.then(newSections => {
            this.sections = newSections;
            this.configChanged();
          })
          .catch(() => {});
      };

      this.sortOptions = {
        axis: 'y',
        delay: 150,
        stop: () => this.configChanged(),
      };

      this.$onInit = initialize;
    },
  });

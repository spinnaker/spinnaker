'use strict';

import * as angular from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';
import _ from 'lodash';

import { SETTINGS } from '../../../config/settings';

import { CORE_APPLICATION_CONFIG_LINKS_EDITLINKS_MODAL_CONTROLLER } from './editLinks.modal.controller';
import { CONFIG_SECTION_FOOTER } from '../footer/configSectionFooter.component';

import './applicationLinks.component.less';

export const CORE_APPLICATION_CONFIG_LINKS_APPLICATIONLINKS_COMPONENT =
  'spinnaker.core.application.config.applicationLinks.component';
export const name = CORE_APPLICATION_CONFIG_LINKS_APPLICATIONLINKS_COMPONENT; // for backwards compatibility
angular
  .module(CORE_APPLICATION_CONFIG_LINKS_APPLICATIONLINKS_COMPONENT, [
    CORE_APPLICATION_CONFIG_LINKS_EDITLINKS_MODAL_CONTROLLER,
    ANGULAR_UI_BOOTSTRAP,
    CONFIG_SECTION_FOOTER,
  ])
  .component('applicationLinks', {
    bindings: {
      application: '=',
    },
    templateUrl: require('./applicationLinks.component.html'),
    controller: [
      '$uibModal',
      function ($uibModal) {
        const initialize = () => {
          if (this.application.notFound || this.application.hasError) {
            return;
          }

          this.cloudProviders = this.application.attributes.cloudProviders
            ? this.application.attributes.cloudProviders
            : [];

          this.sections = _.cloneDeep(this.application.attributes.instanceLinks || SETTINGS.defaultInstanceLinks || [])
            .filter(
              (section) =>
                !section.cloudProviders ||
                section.cloudProviders.length === 0 ||
                _.intersection(section.cloudProviders, this.cloudProviders).length > 0,
            )
            .map((section) => {
              // The absence of cloud providers may be represented as an empty array or a null value. Normalizing this
              // value to an empty array so that we can accurately calculate `isDirty` check in `configChanged`
              // function.
              if (!section.cloudProviders) {
                section.cloudProviders = [];
              }
              return section;
            });

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
          this.usingDefaultLinks = angular.toJson(this.sections) === angular.toJson(SETTINGS.defaultInstanceLinks);
          this.defaultLinksConfigured = !!SETTINGS.defaultInstanceLinks;
        };

        this.revert = initialize;

        this.useDefaultLinks = () => {
          this.sections = _.cloneDeep(SETTINGS.defaultInstanceLinks);
          this.configChanged();
        };

        this.addLink = (section) => {
          section.links.push({ title: '', path: '' });
          this.configChanged();
        };

        this.removeLink = (section, index) => {
          section.links.splice(index, 1);
          this.configChanged();
        };

        this.addSection = () => {
          const section = { title: '', links: [] };
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
          $uibModal
            .open({
              templateUrl: require('./editLinks.modal.html'),
              controller: 'EditLinksModalCtrl as vm',
              size: 'lg modal-fullscreen',
              resolve: {
                sections: () => this.sections,
              },
            })
            .result.then((newSections) => {
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
    ],
  });

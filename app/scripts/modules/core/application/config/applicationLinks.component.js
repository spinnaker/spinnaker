'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.applicationLinks.component', [
    require('../../utils/lodash'),
    require('../service/applications.write.service'),
    require('../../config/settings'),
  ])
  .component('applicationLinks', {
    bindings: {
      application: '=',
    },
    templateUrl: require('./applicationLinks.component.html'),
    controller: function(applicationWriter, settings, _) {

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
      };

      this.revert = initialize;

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
        this.sections.push({title: '', links: []});
        this.configChanged();
      };

      this.removeSection = (index) => {
        this.sections.splice(index, 1);
        this.configChanged();
      };

      this.configChanged = () => {
        this.viewState.isDirty = this.viewState.originalStringVal !== JSON.stringify(angular.copy(this.sections));
      };

      this.$onInit = initialize;
    }
  });

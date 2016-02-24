'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.forms.mapEditor.component', [
    require('../../validation/validateUnique.directive')
  ])
  .component('mapEditor', {
    bindings: {
      model: '=',
      keyLabel: '@',
      valueLabel: '@',
      addButtonLabel: '@',
      allowEmpty: '=?',
      onChange: '&',
    },
    controller: function($scope) {
      this.backingModel = [];

      // Set default values for optional fields
      this.onChange = this.onChange || angular.noop;
      this.keyLabel = this.keyLabel || 'Key';
      this.valueLabel = this.valueLabel || 'Value';
      this.addButtonLabel = this.addButtonLabel || 'Add Field';
      this.allowEmpty = this.allowEmpty || false;

      let modelKeys = () => Object.keys(this.model);

      this.addField = () => {
        this.backingModel.push({ key: '', value: '', checkUnique: modelKeys() });
        // do not fire the onChange event, since no values have been committed to the object
      };

      this.removeField = (index) => {
        this.backingModel.splice(index, 1);
        this.synchronize();
        this.onChange();
      };

      // Clears existing values from model, then replaces them
      this.synchronize = () => {
        let modelStart = JSON.stringify(this.model);
        let allKeys = this.backingModel.map((pair) => pair.key);
        modelKeys().forEach((key) => delete this.model[key]);
        this.backingModel.forEach((pair) => {
          if (pair.key && (this.allowEmpty || pair.value)) {
            this.model[pair.key] = pair.value;
          }
          // include other keys to verify no duplicates
          pair.checkUnique = allKeys.filter((key) => pair.key !== key);
        });
        if (modelStart !== JSON.stringify(this.model)) {
          this.onChange();
        }
      };

      // if defined, the $onInit method will automatically be called when components are initialized
      this.$onInit = () => {
        if (this.model) {
          modelKeys().forEach((key) => {
            this.backingModel.push({key: key, value: this.model[key]});
          });
        }
      };

      $scope.$watch(() => JSON.stringify(this.backingModel), this.synchronize);
    },
    templateUrl: require('./mapEditor.component.html'),
  }
);

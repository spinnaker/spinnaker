'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports =
  angular.module('spinnaker.netflix.pipeline.persistedProperty.property', [])
  .component('property', {
    bindings: {
      property: '=',
      stage: '=',
      index: '=',
      propertyList: '=?'
    },
    controller: 'PropertyController',
    controllerAs: 'propertyCtrl',
    templateUrl: require('./property.component.html'),
  })
  .controller('PropertyController', function () {
    this.filteredProperties = [];

    this.remove = (property) => {
      const index = this.stage.persistedProperties.indexOf(property);
      this.stage.persistedProperties.splice(index, 1);
    };

    this.getValueRowCount = (inputValue) => {
      return inputValue ? inputValue.split(/\n/).length : 1;
    };

    this.refreshOptions = (search) => {
      const newPropKeyList = (this.propertyList || []).map(prop => prop.key);
      if (search && !newPropKeyList.includes(search)) {
        newPropKeyList.unshift(search);
      }
      this.filteredProperties = _.uniq(newPropKeyList);
    };

  });

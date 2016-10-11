'use strict';

import _ from 'lodash';

let angular = require('angular');

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
    let vm = this;

    vm.remove = function(property) {
      var index = vm.stage.persistedProperties.indexOf(property);
      vm.stage.persistedProperties.splice(index, 1);
    };

    vm.getValueRowCount = (inputValue) => {
      return inputValue ? inputValue.split(/\n/).length : 1;
    };

    vm.getPropertyList = _.debounce((search) => {
      let newPropKeyList = vm.propertyList.map(prop => prop.key);
      if (search && !newPropKeyList.includes(search)) {
        newPropKeyList.unshift(search);
      }
      return _.uniq(newPropKeyList);
    }, 200);

  });

'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.projects.dashboard.regionFilter.service', [
    require('../../../utils/lodash.js'),
    require('../../../filterModel/filter.model.service.js'),
  ])
  .factory('regionFilterService', function (filterModelService, _) {
    let callbacks = [];

    let filterModelConfig = [{ model: 'region', param: 'reg', type: 'object' }];
    filterModelService.configureFilterModel(this, filterModelConfig);

    this.registerCallback = (cb) => callbacks.push(cb);

    this.deregisterCallback = (cb) => callbacks = _.without(callbacks, cb);

    this.runCallbacks = () => callbacks.forEach(cb => cb(this.sortFilter.region));

    this.toggleRegion = (region) => {
      let path = `sortFilter.region[${region}]`;
      _.set(this, path, !_.get(this, path));
      this.applyParamsToUrl();
      this.runCallbacks();
    };

    this.clearFilter = () => {
      _.forEach(this.sortFilter.region, (val, key) => delete this.sortFilter.region[key]);
      this.applyParamsToUrl();
      this.runCallbacks();
    };

    this.activate();

    return this;
  });

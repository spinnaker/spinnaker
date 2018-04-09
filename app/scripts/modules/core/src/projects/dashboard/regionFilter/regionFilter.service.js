'use strict';

import _ from 'lodash';
import { FILTER_MODEL_SERVICE } from 'core/filterModel';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.deck.projects.dashboard.regionFilter.service', [FILTER_MODEL_SERVICE])
  .factory('regionFilterService', function(filterModelService) {
    let callbacks = [];

    let filterModelConfig = [{ model: 'region', param: 'reg', type: 'trueKeyObject' }];
    filterModelService.configureFilterModel(this, filterModelConfig);

    this.registerCallback = cb => callbacks.push(cb);

    this.deregisterCallback = cb => (callbacks = _.without(callbacks, cb));

    this.runCallbacks = () => callbacks.forEach(cb => cb(this.sortFilter.region));

    this.toggleRegion = region => {
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

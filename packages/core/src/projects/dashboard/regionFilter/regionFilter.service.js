'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { FilterModelService } from '../../../filterModel';

export const CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_SERVICE =
  'spinnaker.deck.projects.dashboard.regionFilter.service';
export const name = CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_SERVICE; // for backwards compatibility
module(CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_SERVICE, []).factory('regionFilterService', function () {
  let callbacks = [];

  const filterModelConfig = [{ model: 'region', param: 'reg', type: 'trueKeyObject' }];
  FilterModelService.configureFilterModel(this, filterModelConfig);

  this.registerCallback = (cb) => callbacks.push(cb);

  this.deregisterCallback = (cb) => (callbacks = _.without(callbacks, cb));

  this.runCallbacks = () => callbacks.forEach((cb) => cb(this.sortFilter.region));

  this.toggleRegion = (region) => {
    const path = `sortFilter.region[${region}]`;
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

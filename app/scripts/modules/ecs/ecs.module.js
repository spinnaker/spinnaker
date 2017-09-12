'use strict';

let angular = require('angular');

require('./logo/ecs.logo.less');
require('./ecs.settings.ts');

// load all templates into the $templateCache
let templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.ecs', [
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('ecs',
      {
        name: 'EC2 Container Service',
        logo: { path: require('./logo/ecs.logo.svg')},

      });
  });

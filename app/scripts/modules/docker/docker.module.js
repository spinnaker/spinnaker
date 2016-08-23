'use strict';

let angular = require('angular');

// load all templates into the $templateCache
let templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.deck.docker', [
  require('./pipeline/stages/bake/dockerBakeStage.js')
]);

'use strict';

import _ from 'lodash';

const angular = require('angular');

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_IMAGE_REGIONAL_FILTER =
  'spinnaker.azure.serverGroup.configure.basicSettings.image.filter';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_IMAGE_REGIONAL_FILTER; // for backwards compatibility
angular
  .module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_IMAGE_REGIONAL_FILTER, [])
  .filter('regional', function() {
    return function(input, selectedRegion) {
      return _.filter(input, function(image) {
        return image.region === selectedRegion || image.region === null;
      });
    };
  });

'use strict';

import _ from 'lodash';

import {SETTINGS} from 'core/config/settings';

const angular = require('angular');

require('./instanceLinks.component.less');

module.exports = angular
  .module('spinnaker.core.instance.details.instanceLinks', [])
  .component('instanceLinks', {
    bindings: {
      address: '=',
      application: '=',
      instance: '=',
    },
    templateUrl: require('./instanceLinks.component.html'),
    controller: function($interpolate) {
      this.port = _.get(this.application, 'attributes.instancePort', SETTINGS.defaultInstancePort) || 80;
      this.sections = _.cloneDeep(_.get(this.application, 'attributes.instanceLinks', SETTINGS.defaultInstanceLinks) || []);
      this.sections.forEach(section => {
        section.links = section.links.map(link => {
          let port = link.path.indexOf(':') === 0 || !this.port ? '' : ':' + this.port;
          let url = link.path;
          // handle interpolated variables
          if (url.includes('{{')) {
            url = $interpolate(url)(this.instance);
          }
          // handle relative paths
          if (!url.includes('//')) {
            url = `http://${this.address + port + url}`;
          }
          return {
            url: url,
            title: link.title || link.path
          };
        });
      });
    }
  });

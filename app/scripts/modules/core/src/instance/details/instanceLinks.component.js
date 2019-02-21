'use strict';

const angular = require('angular');
import _ from 'lodash';

import { SETTINGS } from 'core/config/settings';

import './instanceLinks.component.less';

module.exports = angular.module('spinnaker.core.instance.details.instanceLinks', []).component('instanceLinks', {
  bindings: {
    address: '=',
    application: '=',
    instance: '=',
    moniker: '=',
    environment: '=',
  },
  templateUrl: require('./instanceLinks.component.html'),
  controller: [
    '$interpolate',
    function($interpolate) {
      this.port = _.get(this.application, 'attributes.instancePort', SETTINGS.defaultInstancePort) || 80;
      this.sections = _.cloneDeep(
        _.get(this.application, 'attributes.instanceLinks', SETTINGS.defaultInstanceLinks) || [],
      ).filter(
        section =>
          !section.cloudProviders ||
          !this.instance.cloudProvider ||
          section.cloudProviders.includes(this.instance.cloudProvider),
      );
      this.sections.forEach(section => {
        section.links = section.links.map(link => {
          let port = link.path.indexOf(':') === 0 || !this.port ? '' : ':' + this.port;
          let url = link.path;
          // handle interpolated variables
          if (url.includes('{{')) {
            url = $interpolate(url)(
              Object.assign({}, this.instance, this.moniker, {
                ipAddress: this.address,
                environment: this.environment,
              }),
            );
          }
          // handle relative paths
          if (!url.includes('//') && !url.startsWith('{{')) {
            url = `http://${this.address + port + url}`;
          }
          return {
            url: url,
            title: link.title || link.path,
          };
        });
      });
    },
  ],
});

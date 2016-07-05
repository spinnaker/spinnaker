'use strict';

const angular = require('angular');

require('./instanceLinks.component.less');

module.exports = angular
  .module('spinnaker.core.instance.details.instanceLinks', [
    require('../../config/settings'),
    require('../../utils/lodash')
  ])
  .component('instanceLinks', {
    bindings: {
      address: '=',
      application: '=',
      instance: '=',
    },
    templateUrl: require('./instanceLinks.component.html'),
    controller: function(settings, _, $interpolate) {
      this.port = _.get(this.application, 'attributes.instancePort', settings.defaultInstancePort) || 80;
      this.sections = _.cloneDeep(_.get(this.application, 'attributes.instanceLinks', settings.defaultInstanceLinks) || []);
      this.sections.forEach(section => {
        section.links = section.links.map(link => {
          let port = link.path.indexOf(':') === 0 || !this.port ? '' : ':' + this.port;
          let url = link.path;
          // handle interpolated variables
          if (url.indexOf('{{') > -1) {
            url = $interpolate(url)(this.instance);
          }
          // handle relative paths
          if (url.indexOf('//') === -1) {
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

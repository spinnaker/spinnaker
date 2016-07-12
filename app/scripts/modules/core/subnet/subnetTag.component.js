'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.subnet.tag.component', [
  require('./subnet.read.service.js'),
])
  .component('subnetTag', {
    bindings: {
      subnetId: '=',
    },
    template: '<span class="subnet-tag">{{$ctrl.subnetLabel}}</span>',
    controller: function(subnetReader) {
      this.$onInit = () => {
        if (!this.subnetId) {
          this.subnetLabel = null;
        } else {
          this.subnetLabel = '(' + this.subnetId + ')';
          subnetReader.getSubnetPurpose(this.subnetId).then(name => {
            if (name) {
              this.subnetLabel = name;
            }
          });
        }
      };
    }
  });

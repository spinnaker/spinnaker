'use strict';

import { SubnetReader } from 'core/subnet/subnet.read.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.subnet.tag.component', []).component('subnetTag', {
  bindings: {
    subnetId: '=',
  },
  template: '<span class="subnet-tag">{{$ctrl.subnetLabel}}</span>',
  controller: function() {
    this.$onInit = () => {
      if (!this.subnetId) {
        this.subnetLabel = null;
      } else {
        this.subnetLabel = '(' + this.subnetId + ')';
        SubnetReader.getSubnetPurpose(this.subnetId).then(name => {
          if (name) {
            this.subnetLabel = name;
          }
        });
      }
    };
  },
});

'use strict';

import {SUBNET_READ_SERVICE} from 'core/subnet/subnet.read.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.subnet.tag.component', [
  SUBNET_READ_SERVICE,
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

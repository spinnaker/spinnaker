'use strict';

module.exports = class {
  constructor (appName) {
    this.url = `/#/applications/${appName}/loadBalancers`;
    this.createLoadBalancerButton = element(by.buttonText('Create Load Balancer'));
  }
};

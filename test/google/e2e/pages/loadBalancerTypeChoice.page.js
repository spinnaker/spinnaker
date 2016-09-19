'use strict';

module.exports = class {
  constructor () {
    this.typeDropdown = element(by.model('ctrl.choice'));
    this.networkLoadBalancer = this.typeDropdown.all(by.css('.ui-select-choices-row')).get(0);
  }

  submitButton (type) {
    return element(by.buttonText(`Create ${type} load balancer`));
  }
};

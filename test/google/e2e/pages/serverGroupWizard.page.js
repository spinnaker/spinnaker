'use strict';

module.exports = class {
  constructor() {
    this.imageSelector = element.all(by.css('.ui-select-toggle')).get(0);
    this.firstImage = element.all(by.css('.ui-select-choices-row')).get(0);

    this.instanceTypeSelector = element(by.model('command.instanceType'));
    this.f1Micro = this.instanceTypeSelector.element(by.css('[label="f1-micro"]'));

    this.createButton = element(by.buttonText('Create'));
  }
};

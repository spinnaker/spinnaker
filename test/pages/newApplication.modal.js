'use strict';

module.exports = {
  header: element(by.css('[data-purpose="modal-header"]')),
  description: element(by.css('[data-purpose="application-description"]')),
  email: element(by.css('[data-purpose="application-email"]')),
  name: element(by.css('[data-purpose="application-name"]')),
  uniqueNameWarning: element(by.css('.error-message')).element(by.tagName('span')),
  //prod: element(by.css('[data-purpose="application-accounts"] [value="prod"]')),
  prod: element(by.cssContainingText('option', 'prod')),
  submit: element(by.css('[data-purpose="submit"]')),

  typeName: function(name) {
    this.name.sendKeys(name);
  },

  typeEmail: function(email) {
    this.email.sendKeys(email);
  },

  typeDescription: function(description) {
    this.description.sendKeys(description) ;
  },

  selectProdAccount: function () {
    return this.prod.click();
  },

  submitForm: function () {
    return this.submit.click();
  },

  resetForm: function() {
    this.name.sendKeys('');
    this.description.sendKeys('');
    this.email.sendKeys('');
  }
};

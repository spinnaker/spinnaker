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
  accountSelectBox: element(by.css('.select2-input')),


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

  selectTestAccount: function () {
    this.accountSelectBox.click().then(function() {
      element(by.css('li#ui-select-choices-row-0-0 div span')).click();
    });
  },

  submitForm: function () {
    return this.submit.click();
  },

  resetForm: function() {
    this.name.clear();
    this.description.clear();
    this.email.clear();
    element.all(by.css('.ui-select-match-close')).then(function(list) {
      list.forEach(function(deleteIcon) {
        deleteIcon.click();
      });
    });
  }
};

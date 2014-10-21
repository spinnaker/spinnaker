'use strict';

var page = require('../pages/applications.js');
var newApplicationModal = require('../pages/newApplicationModal.js');
var Q = require('q');

describe('applications view', function() {
  beforeEach(function() {
    browser.ignoreSynchronization = true;
  });

  it('should redirect to /applications from /', function() {
    browser.get('/');
    expect(page.header.getText()).toEqual('Applications');
  });

  it('should create an application', function() {
    browser.get(page.url);
    page.menu.click().then(function() {
      expect(page.createApplicationMenuItem.isDisplayed()).toBe(true);
      page.createApplicationMenuItem.click().then(function() {
        browser.sleep(200);
        expect(newApplicationModal.header.isDisplayed()).toBe(true);
        Q.all([
          newApplicationModal.name.sendKeys('deck-e2e-test'),
          newApplicationModal.description.sendKeys('a deck test'),
          newApplicationModal.email.sendKeys('delivery-engineering+deck-e2e@netflix.com'),
          newApplicationModal.prod.click(),
        ]).then(function() {
          newApplicationModal.submit.click();
        });
      });
    });
  });
});

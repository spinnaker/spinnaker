'use strict';

var page = require('../pages/applications.js');

describe('applications view', function() {
  beforeEach(function() {
    browser.ignoreSynchronization = true;
  });

  it('should redirect to /applications from /', function() {
    browser.get('/');
    expect(page.header.getText()).toEqual('Applications');
  });

  describe('creating an application', function() {
    it('should open the modal', function() {
      browser.get(page.url);
      page.menu.click().then(function() {
        expect(page.createApplicationMenuItem.isDisplayed()).toBe(true);
        page.createApplicationMenuItem.click().then(function() {
          browser.sleep(200);
          expect(page.newApplicationHeader.isDisplayed()).toBe(true);
        });
      });
    });
  });
});

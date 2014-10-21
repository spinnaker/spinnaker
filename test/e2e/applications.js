'use strict';

var page = require('../pages/applications.js');
var newApplicationModal = require('../pages/newApplicationModal.js');
var Q = require('q');

describe('applications view', function() {
  var browserName;

  beforeEach(function(done) {
    browser.getCapabilities().then(function(capabilities) {
      browserName = capabilities.caps_.browserName;
      done();
    });
  });

  beforeEach(function() {
    this.openMenu = function() {
      browser.get(page.url);
      return page.menu.click();
    };
  });

  it('should redirect to /applications from /', function() {
    browser.get('/');
    expect(page.header.getText()).toEqual('Applications');
  });

  describe('the Actions menu', function() {

    describe('Create Application', function () {
      it('is accessed from the Actions menu', function(done) {
        this.openMenu().then(function() {
          done();
        });
      });

      it('launches the Create Application modal', function(done) {
        browser.wait(page.createApplicationMenuItem.isDisplayed,
          undefined,
          'Failed to open the Actions menu').then(function() {
          page.createApplicationMenuItem.click().then(function() {
            browser.wait(newApplicationModal.header.isDisplayed,
              undefined,
              'Failed to launch the new application modal').then(function() {
                done(); // done() fails when provided with an argument :(
              }, console.log);
          }, console.log);
        }, console.log);
      });

      it('takes a name, email, description and account', function(done) {
        Q.all([
          newApplicationModal.prod.click(),
        ]).then(function() {
          expect(newApplicationModal.submit.getAttribute('disabled')).not.toBe(null);
        });

        Q.all([
          newApplicationModal.name.sendKeys('deck-e2e-test-'+browserName),
          newApplicationModal.description.sendKeys('a deck test'),
          newApplicationModal.email.sendKeys('delivery-engineering+deck-e2e@netflix.com'),
          newApplicationModal.prod.click(),
        ]).then(function() {
          expect(newApplicationModal.submit.getAttribute('disabled')).toBe(null);
          done();
        }, console.log);
      });

      it('creates the application', function(done) {
        // TODO: application creation is currently busted in prestaging
        newApplicationModal.submit.click().then(function() {
          done();
        }, console.log);
      });
    });
  });
});

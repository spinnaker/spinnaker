'use strict';

var page = require('../pages/applications.page.js');
//var Modal = require('../pages/newApplication.modal.js');
var Q = require('q');

var newApplicationModal = require('../pages/newApplication.modal.js');

describe('Applications Landing Page', function() {
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

  describe('Rerouting: ', function () {

    it('should redirect to /applications from /', function() {
      browser.get('/');
      expect(page.header.getText()).toEqual('Applications');
    });

  });

  describe('The Actions Button', function() {

    describe('Create Application', function () {

      it('Click the Actions Button to display Create Application action', function(done) {
        this.openMenu().then(function() {
          done();
        });
      });

      it('Clicking the Create Action Link launches the modal', function(done) {
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

      describe('Test the new application modal form', function () {

        afterEach(function() {
          newApplicationModal.resetForm();
        });

        it('should have an disabled submit button when the form is not filled out', function () {
          expect(newApplicationModal.submit.getAttribute('disabled')).not.toBe(null);
        });

        it('should give a warning if the application name is not unique', function () {
          newApplicationModal.typeName('deck');
          expect(newApplicationModal.uniqueNameWarning.isPresent()).toBe(true);
          expect(newApplicationModal.uniqueNameWarning.getText()).toContain('unique');
        });

        it('takes a name, email, description and account', function() {
          newApplicationModal.typeName('deck_e2e_test_'+browserName);
          newApplicationModal.typeDescription('a deck test');
          newApplicationModal.typeEmail('delivery-engineering+deck-e2e@netflix.com');
          newApplicationModal.selectTestAccount();

          expect(newApplicationModal.submit.getAttribute('disabled')).toBe(null);
        });

        it('creates the application', function(done) {
          // TODO: application creation is currently busted in prestaging
          newApplicationModal.typeName('deck.e2e.'+browserName);
          newApplicationModal.typeDescription('a deck test');
          newApplicationModal.typeEmail('delivery-engineering+deck-e2e@netflix.com');
          newApplicationModal.selectTestAccount();

          newApplicationModal.submitForm().then(
            function() {
              done();
            },
            console.log
          );
        });
      });
    });
  });
});

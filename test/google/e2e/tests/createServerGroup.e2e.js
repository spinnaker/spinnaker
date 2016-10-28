'use strict';

let { createApp, deleteApp } = require('../../tasks'),
  orchestrator = require('../../tasks/orchestrator'),
  ClusterPage = require('../pages/cluster.page'),
  ServerGroupWizard = require('../pages/serverGroupWizard.page.js');

describe('Create Server Group', function () {
  let appName = `test${Date.now()}`,
    page = new ClusterPage(appName),
    modal = new ServerGroupWizard();

  beforeAll(function (done) {
    let tasks = [createApp];

    orchestrator({ appName, tasks })
      .then(() => done(), done);
  });

  beforeAll(function () {
    browser.get(page.url);
    browser.ignoreSynchronization = true;
    browser.driver.manage().window().maximize();
    browser.sleep(1000);
  });

  afterAll(function (done) {
    let tasks = [deleteApp];

    orchestrator({ appName, tasks })
      .then(() => done(), done);
  });

  describe('Create server group modal', function () {
    it('should allow the user to create server group with minimal configuration', function () {
      let openWizard = page.createServerGroupButton;
      openWizard.click();

      // This sleep is probably necessary because the images have to load.
      browser.sleep(1000);
      modal.imageSelector.click();

      /*
        For whatever reason, the dropdown does not open properly, and the mouse action is necessary.
        The behavior inside the test does not match the behavior inside the protractor REPL.
      */
      browser.actions().mouseDown(modal.imageSelector).perform();
      browser.sleep(1000);
      modal.firstImage.click();

      // Scrolls down modal to instanceTypeSelector.
      browser.actions().mouseMove(modal.instanceTypeSelector).perform();
      modal.f1Micro.click();

      expect(modal.createButton.isDisplayed()).toBeTruthy();
      expect(modal.createButton.isEnabled()).toBeTruthy();
    });
  });

});

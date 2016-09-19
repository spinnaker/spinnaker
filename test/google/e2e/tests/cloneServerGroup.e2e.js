'use strict';

let { createApp, createServerGroup, deleteApp } = require('../../tasks'),
  orchestrator = require('../../tasks/orchestrator'),
  ClusterPage = require('../pages/cluster.page.js'),
  ServerGroupWizard = require('../pages/serverGroupWizard.page');

describe('Clone Server Group', function () {
  let appName = `test${Date.now()}`,
    serverGroupName = `${appName}-v000`,
    page = new ClusterPage(appName),
    modal = new ServerGroupWizard();

  beforeAll(function (done) {
    let tasks = [createApp, createServerGroup];

    orchestrator({ appName, tasks })
      .then(() => done(), done);
  });

  beforeAll(function () {
    browser.ignoreSynchronization = true;
    browser.get(page.getServerGroupDetailUrl(serverGroupName));
    browser.sleep(1000);
    browser.driver.manage().window().maximize();
  });


  describe('Clone server group modal', function () {
    it('should allow the user to clone a server group with no configuration', function () {
      page.serverGroupActionsButton.click();
      page.cloneServerGroupButton.click();

      expect(modal.createButton.isDisplayed()).toBeTruthy();
      expect(modal.createButton.isEnabled()).toBeTruthy();
    });
  });
});

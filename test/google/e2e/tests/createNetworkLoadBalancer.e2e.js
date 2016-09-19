'use strict';

let { createApp, deleteApp } = require('../../tasks'),
  orchestrator = require('../../tasks/orchestrator'),
  LoadBalancerPage = require('../pages/loadBalancer.page'),
  LoadBalancerTypeChoice = require('../pages/loadBalancerTypeChoice.page'),
  LoadBalancerWizard = require('../pages/loadBalancerWizard.page');

describe('Create Load Balancer', function () {
  let appName = `test${Date.now()}`,
    page = new LoadBalancerPage(appName),
    typeChoice = new LoadBalancerTypeChoice(),
    modal = new LoadBalancerWizard();

  beforeAll(function (done) {
    let tasks = [createApp];

    orchestrator({ appName, tasks })
      .then(() => done(), done);
  });

  beforeAll(function () {
    browser.ignoreSynchronization = true;
    browser.get(page.url);
    browser.sleep(1000);
    browser.driver.manage().window().maximize();
  });

  afterAll(function (done) {
    let tasks = [deleteApp];

    orchestrator({ appName, tasks })
      .then(() => done(), done);
  });

  describe('Create load balancer modal', function () {
    it('should allow the user to create a network load balancer with no configuration', function () {
      page.createLoadBalancerButton.click();

      typeChoice.typeDropdown.click();
      typeChoice.networkLoadBalancer.click();
      typeChoice.submitButton('Network').click();

      expect(modal.createButton.isDisplayed()).toBeTruthy();
      expect(modal.createButton.isEnabled()).toBeTruthy();
    });
  });
});

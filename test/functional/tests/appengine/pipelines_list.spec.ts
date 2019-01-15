import { HomePage } from '../core/pages/HomePage';
import { ApplicationsListPage } from '../core/pages/ApplicationsListPage';
import { InfrastructurePage } from '../core/pages/InfrastructurePage';
import { PipelinesListPage } from '../core/pages/PipelinesListPage';

describe('AppEngine Pipelines', () => {
  describe('List', () => {
    it('shows stored appengine pipelines with their provider tag', () => {
      const home = new HomePage();
      const apps = new ApplicationsListPage();
      const infra = new InfrastructurePage();
      const pipelines = new PipelinesListPage();
      home.open();
      home.click(HomePage.locators.headerNav.applicationsButton);
      apps.clickOnApplicationLink('gae');
      infra.click(InfrastructurePage.locators.headerNav.pipelinesButton);
      pipelines.awaitLocator(PipelinesListPage.locators.executionGroup);
      const executionGroups = browser.$$(PipelinesListPage.locators.executionGroup);
      const firstAccountTag = browser.$(PipelinesListPage.locators.accountTag);
      expect(executionGroups.length).toBe(4);
      expect(firstAccountTag.getText()).toEqual('GAE');
    });
  });
});

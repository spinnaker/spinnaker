import { HomePage } from '../core/pages/HomePage';
import { ApplicationsListPage } from '../core/pages/ApplicationsListPage';
import { InfrastructurePage } from '../core/pages/InfrastructurePage';
import { PipelinesListPage } from '../core/pages/PipelinesListPage';
import { HeaderNavLocators } from '../core/locators/HeaderNavLocators';

describe('Compute Pipelines', () => {
  describe('List', () => {
    it('shows stored GCE pipelines with their account tag', () => {
      const home = new HomePage();
      const apps = new ApplicationsListPage();
      const infra = new InfrastructurePage();
      const pipelines = new PipelinesListPage();
      home.open();
      home.click(HeaderNavLocators.applicationsButton);
      apps.clickOnApplicationLink('compute');
      infra.click(HeaderNavLocators.pipelinesButton);
      pipelines.awaitLocator(PipelinesListPage.locators.executionGroup);
      const executionGroups = browser.$$(PipelinesListPage.locators.executionGroup);
      const firstAccountTag = browser.$(PipelinesListPage.locators.accountTag);
      expect(executionGroups.length).toBe(9);
      expect(firstAccountTag.getText()).toEqual('GCE');
    });
  });
});

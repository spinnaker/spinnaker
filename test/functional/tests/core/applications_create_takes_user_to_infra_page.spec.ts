import { HomePage } from './pages/HomePage';
import { ApplicationsListPage } from './pages/ApplicationsListPage';
import { InfrastructurePage } from './pages/InfrastructurePage';
import { HeaderNavLocators } from './locators/HeaderNavLocators';
import { NewApplicationModalLocators } from './locators/NewApplicationModalLocators';

describe('Applications', () => {
  describe('Create', () => {
    const home = new HomePage();
    const apps = new ApplicationsListPage();
    const infra = new InfrastructurePage();

    const testAppName = 'testapp1';

    afterAll(() => {
      apps.deleteAppIfExists(testAppName);
    });

    it('takes the user to their application infrastructure on success', () => {
      home.open();
      home.click(HeaderNavLocators.applicationsButton);
      apps.openActionsMenu();
      apps.clickMenuItem('Create Application');
      apps.enterNewApplicationName(testAppName);
      apps.enterNewApplicationEmail('user@testcompany.com');
      apps.click(NewApplicationModalLocators.createButton);
      infra.awaitLocator(HeaderNavLocators.applicationName, 30000);
      expect($(HeaderNavLocators.applicationName).getText()).toBe(testAppName);
    });
  });
});

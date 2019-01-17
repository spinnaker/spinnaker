import { HomePage } from './pages/HomePage';
import { ApplicationsListPage } from './pages/ApplicationsListPage';
import { HeaderNavLocators } from './locators/HeaderNavLocators';
import { NewApplicationModalLocators } from './locators/NewApplicationModalLocators';

describe('Applications', () => {
  describe('Create', () => {
    const home = new HomePage();
    const apps = new ApplicationsListPage();

    it(`shows a config screen with required fields preventing creation until they're populated`, () => {
      home.open();
      home.click(HeaderNavLocators.applicationsButton);
      apps.openActionsMenu();
      apps.clickMenuItem('Create Application');
      const createButtonLocator = NewApplicationModalLocators.createButton;
      const isCreateButtonEnabled = (): boolean => $(createButtonLocator).isEnabled() as boolean;
      expect(isCreateButtonEnabled()).toBe(false);
      apps.enterNewApplicationName('testapp');
      expect(isCreateButtonEnabled()).toBe(false);
      apps.enterNewApplicationEmail('user@testcompany.com');
      expect(isCreateButtonEnabled()).toBe(true);
    });
  });
});

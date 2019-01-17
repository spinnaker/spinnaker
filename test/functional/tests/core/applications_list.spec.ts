import { HomePage } from './pages/HomePage';
import { ApplicationsListPage } from './pages/ApplicationsListPage';
import { HeaderNavLocators } from './locators/HeaderNavLocators';

describe('Applications', () => {
  describe('List', () => {
    it('shows a list of applications', () => {
      const home = new HomePage();
      const apps = new ApplicationsListPage();
      home.open();
      home.click(HeaderNavLocators.applicationsButton);
      const computeApp = apps.applicationLinkWithLabel('compute');
      expect(computeApp.isExisting()).toBeTruthy();
      const gaeApp = apps.applicationLinkWithLabel('gae');
      expect(gaeApp.isExisting()).toBeTruthy();
      const gkeApp = apps.applicationLinkWithLabel('gke');
      expect(gkeApp.isExisting()).toBeTruthy();
    });
  });
});

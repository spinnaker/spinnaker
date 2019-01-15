import { HomePage } from './pages/HomePage';
import { ApplicationsListPage } from './pages/ApplicationsListPage';

describe('Applications', () => {
  describe('List', () => {
    it('shows a list of applications', () => {
      const home = new HomePage();
      home.open();
      home.click(HomePage.locators.headerNav.applicationsButton);
      const applicationLinks = browser.$$(ApplicationsListPage.locators.applicationLinks);
      expect(applicationLinks.length).toBe(11);
      expect(applicationLinks[0].getText()).toBe('compute');
      expect(applicationLinks[1].getText()).toBe('decktests');
      expect(applicationLinks[2].getText()).toBe('default');
      expect(applicationLinks[3].getText()).toBe('gae');
      expect(applicationLinks[4].getText()).toBe('gke');
      expect(applicationLinks[5].getText()).toBe('k8sdeploy');
      expect(applicationLinks[6].getText()).toBe('k8sv1');
      expect(applicationLinks[7].getText()).toBe('k8sv2');
      expect(applicationLinks[8].getText()).toBe('kubekayentacodelab');
      expect(applicationLinks[9].getText()).toBe('multiaccount');
      expect(applicationLinks[10].getText()).toBe('20181101t094428');
    });
  });
});

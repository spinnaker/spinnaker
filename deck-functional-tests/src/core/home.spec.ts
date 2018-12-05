import { HomePage } from './pages/HomePage';

describe('Home page', function() {
  it('shows a search input field on load', function() {
    const homePage = new HomePage();
    homePage.open();
    browser.waitForExist(homePage.searchInput(), 1000);
  });
});

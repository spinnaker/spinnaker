import { Page } from './Page';

export class HomePage extends Page {
  public static locators = {
    searchInput: '.header-section input[type="search"]',
  };

  public searchInput(): string {
    return HomePage.locators.searchInput;
  }

  public open(_url = '') {
    return browser.url('/');
  }
}

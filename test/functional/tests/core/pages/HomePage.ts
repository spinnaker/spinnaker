import { Page } from './Page';
import { HeaderNavLocators } from '../locators/HeaderNavLocators';

export class HomePage extends Page {
  public static locators = {
    searchInput: '.header-section input[type="search"]',
    headerNav: HeaderNavLocators,
  };

  public searchInput(): string {
    return HomePage.locators.searchInput;
  }

  public open(_url = '') {
    return browser.url('/');
  }
}

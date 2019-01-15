import { Page } from './Page';
import { HeaderNavLocators } from '../locators/HeaderNavLocators';

export class InfrastructurePage extends Page {
  public static locators = {
    clickableServerGroup: '.server-group.clickable',
    actionsButton: '.details-panel .actions button',
    cloneMenuItem: `//*[contains(@class, 'dropdown-menu')]//a[contains(text(), 'Clone')]`,
    headerNav: HeaderNavLocators,
  };

  public openClustersForApplication(application: string) {
    return browser.url(`/#/applications/${application}/clusters`);
  }
}

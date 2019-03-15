import { Page } from './Page';
import { HomePage } from './HomePage';
import { ApplicationConfigPage } from './ApplicationConfigPage';
import { NewApplicationModalLocators } from '../locators/NewApplicationModalLocators';
import { Client, RawResult, Element } from 'webdriverio';

export class ApplicationsListPage extends Page {
  public static locators = {
    applicationLinks: 'tbody td a',
    actionsMenu: '#insight-menu',
    factories: {
      applicationLink: (name: string) => `//tbody//td//a[contains(text(), '${name}')]`,
      actionsMenuItemLink: (label: string) =>
        `//*[contains(@class, 'header-actions')]//a[contains(text(), '${label}')]`,
    },
  };

  public open(_url = '') {
    return browser.url('/#/applications');
  }

  public clickOnApplicationLink(appName: string) {
    this.click(ApplicationsListPage.locators.factories.applicationLink(appName));
  }

  public openActionsMenu() {
    this.click(ApplicationsListPage.locators.actionsMenu);
  }

  public clickMenuItem(itemLabel: string) {
    this.click(ApplicationsListPage.locators.factories.actionsMenuItemLink(itemLabel));
  }

  public enterNewApplicationName(name: string) {
    this.setInputText(NewApplicationModalLocators.nameInput, name);
  }

  public enterNewApplicationEmail(email: string) {
    this.setInputText(NewApplicationModalLocators.emailInput, email);
  }

  public applicationsLinks(): Client<RawResult<Element>>[] & RawResult<Element>[] {
    this.awaitLocator(ApplicationsListPage.locators.applicationLinks);
    return browser.$$(ApplicationsListPage.locators.applicationLinks);
  }

  public applicationLinkWithLabel(label: string): Client<RawResult<Element>> {
    const links = this.applicationsLinks();
    const labelLink = links.find(link => link.getText().includes(label));
    return labelLink;
  }

  public deleteAppIfExists(label: string) {
    const configPage = new ApplicationConfigPage();
    configPage.openForApp(label, 'delete');
    browser.pause(500);
    const url = browser.getUrl();
    if (!url.includes('section=delete')) {
      // Browser will redirect to home page if a spinnaker app with $label doesn't exist
      return;
    }
    this.awaitLocator(ApplicationConfigPage.locators.deleteButton);
    const deleteLink = browser.$(ApplicationConfigPage.locators.deleteButton);
    deleteLink.click();
    const confirmDeleteLocator = ApplicationConfigPage.locators.factories.confirmDeleteButton(label);
    this.awaitLocator(confirmDeleteLocator);
    browser.$(confirmDeleteLocator).click();
    this.awaitLocator(HomePage.locators.searchInput);
  }
}

import { Page } from './Page';

export class ApplicationsListPage extends Page {
  public static locators = {
    applicationLinks: 'tbody td a',
    factories: {
      applicationLink: (name: string) => `//tbody//td//a[contains(text(), '${name}')]`,
    },
  };

  clickOnApplicationLink(appName: string) {
    this.click(ApplicationsListPage.locators.factories.applicationLink(appName));
  }
}

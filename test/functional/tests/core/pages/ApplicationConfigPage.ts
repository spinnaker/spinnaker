import { Page } from './Page';

export class ApplicationConfigPage extends Page {
  public static locators = {
    deleteButton: '//button[contains(., "Delete App")]',
    factories: {
      confirmDeleteButton: (appLabel: string) => `//button[contains(., 'Delete ${appLabel}')]`,
    },
  };

  public openForApp(appName: string, section = '') {
    let url = `/#/applications/${appName}/config`;
    if (section) {
      url = `${url}?section=${section}`;
    }
    browser.url(url);
  }
}

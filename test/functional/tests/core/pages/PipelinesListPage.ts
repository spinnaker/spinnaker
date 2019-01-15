import { Page } from './Page';

export class PipelinesListPage extends Page {
  public static locators = {
    executionGroup: '.execution-group',
    accountTag: '.account-tag',
  };
}

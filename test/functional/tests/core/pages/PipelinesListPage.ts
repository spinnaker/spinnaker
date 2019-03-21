import { Page } from './Page';

export class PipelinesListPage extends Page {
  public static locators = {
    executionGroup: '.execution-group',
    accountTag: '.account-tag',
    manualExecutionLink: '//span[contains(., "Start Manual Execution")]',

    factories: {
      pipelineWithStatus: (status: string) =>
        `//*[contains(@class, "execution-summary")]//span[contains(@class, "execution-status") and contains(., "${status}")]`,
    },
  };
}

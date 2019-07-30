import { Page } from './Page';

export class ManualExecutionModalPage extends Page {
  public static locators = {
    pipelineSelectArrow: '//div[contains(@class, "modal-content")]//span[contains(@class, "Select-arrow-zone")]',
    runButton:
      '//div[contains(@class, "modal-content")]//button[contains(@class, "btn-primary") and contains(., "Run")]',

    factories: {
      pipelineWithName: (name: string) => `//*[contains(@class, 'Select-menu')]//div[contains(text(), '${name}')]`,
    },
  };

  runPipeline = (name: string) => {
    this.click(ManualExecutionModalPage.locators.pipelineSelectArrow);
    this.click(ManualExecutionModalPage.locators.factories.pipelineWithName(name));
    this.click(ManualExecutionModalPage.locators.runButton);
  };
}

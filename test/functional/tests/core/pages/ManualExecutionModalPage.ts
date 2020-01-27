import { Page } from './Page';

export class ManualExecutionModalPage extends Page {
  public static locators = {
    pipelineSelectArrow: '//div[contains(@class, "modal-content")]//span[contains(@class, "Select-arrow-zone")]',
    runButton:
      '//div[contains(@class, "modal-content")]//button[contains(@class, "btn-primary") and contains(., "Run")]',

    paramWithLabel:
      '//div[contains(@class, "modal-content")]//div[contains(@class, "form-group")]//label[text()="labeled param1"]',
    paramWithoutLabel:
      '//div[contains(@class, "modal-content")]//div[contains(@class, "form-group")]//label[text()="param2"]',
    notificationsLabel:
      '//div[contains(@class, "modal-content")]//div[contains(@class, "form-group")]//label[text()="Notifications"]',
    notificationsValue:
      '//div[contains(@class, "modal-content")]//div[contains(@class, "form-group")]//input[@type="checkbox" and @name="notificationEnabled"]',

    factories: {
      pipelineWithName: (name: string) => `//*[contains(@class, 'Select-menu')]//div[contains(text(), '${name}')]`,
    },
  };

  runPipeline = (name: string) => {
    this.click(ManualExecutionModalPage.locators.pipelineSelectArrow);
    this.click(ManualExecutionModalPage.locators.factories.pipelineWithName(name));
    this.awaitLocator(ManualExecutionModalPage.locators.notificationsLabel);
    this.awaitLocator(ManualExecutionModalPage.locators.notificationsValue);
    this.awaitLocator(ManualExecutionModalPage.locators.paramWithLabel);
    this.awaitLocator(ManualExecutionModalPage.locators.paramWithoutLabel);
    this.click(ManualExecutionModalPage.locators.runButton);
  };
}

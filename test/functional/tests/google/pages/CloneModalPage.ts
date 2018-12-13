import { Page } from '../../core/pages/Page';

export class CloneModalPage extends Page {
  public static locators = {
    imageSelect: `//a[contains(@placeholder, 'Search for an image')]`,
    imageInput: `//*[contains(@ng-model, 'command.image')]//*[contains(@class, 'search-container')]/input`,
    customInstanceCoresSelectArrow: `(//gce-custom-instance-configurer//*[@class = 'Select-arrow'])[1]`,
    customInstanceMemorySelectArrow: `(//gce-custom-instance-configurer//*[@class = 'Select-arrow'])[2]`,
    customInstanceDropdownListItems: `(//gce-custom-instance-configurer//*[contains(@class, 'Select-option')])`,

    factories: {
      machineImageFromName: (name: string) =>
        `//*[contains(@class, 'select2-result-label')]//*[contains(text(), '${name}')]`,
      machineTypeFromLabel: (label: string) =>
        `//*[contains(@class, 'instance-profile') and descendant::text()[contains(., '${label}')]]`,
    },
  };

  public selectMachineImage(partialImageName: string) {
    this.click(CloneModalPage.locators.imageSelect);
    this.setInputText(CloneModalPage.locators.imageInput, 'ubuntu');
    this.click(CloneModalPage.locators.factories.machineImageFromName(partialImageName));
  }

  public selectMachineInstanceType(typeLabel: string) {
    this.scrollTo(CloneModalPage.locators.factories.machineTypeFromLabel(typeLabel));
    this.click(CloneModalPage.locators.factories.machineTypeFromLabel(typeLabel));
  }

  public getAvailableCustomInstanceCores(): any {
    this.awaitLocator(CloneModalPage.locators.customInstanceCoresSelectArrow);
    this.click(CloneModalPage.locators.customInstanceCoresSelectArrow);
    browser.pause(300); // give the dropdown a moment to appear
    const cores = browser
      .elements(CloneModalPage.locators.customInstanceDropdownListItems)
      .value.map((item: any) => item.getText());
    this.click(CloneModalPage.locators.customInstanceCoresSelectArrow);
    return cores;
  }

  public getAvailableCustomInstanceMemory(): any {
    this.awaitLocator(CloneModalPage.locators.customInstanceMemorySelectArrow);
    this.click(CloneModalPage.locators.customInstanceMemorySelectArrow);
    browser.pause(300); // give the dropdown a moment to appear
    const memory = browser
      .elements(CloneModalPage.locators.customInstanceDropdownListItems)
      .value.map((item: any) => item.getText());
    this.click(CloneModalPage.locators.customInstanceMemorySelectArrow);
    return memory;
  }
}

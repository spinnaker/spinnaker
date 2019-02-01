import { Page } from '../../core/pages/Page';

export class CreateServerGroupModalPage extends Page {
  public static locators = {
    createServerGroupHeading: `//h3[contains(., 'Create New Server Group')]`,
    acceleratorSectionHeading: `//*[contains(@class, 'sm-label-left') and contains(., 'Accelerators')]`,
    addAcceleratorButton: `//button[contains(., 'Add Accelerator')]`,
    acceleratorTypeSelect: `//gce-accelerator-configurer//a[contains(@class, 'ui-select-match')]`,
    acceleratorDropdownListItems: `(//ul[contains(@class, 'ui-select-choices-content')]//li//span)`,
    regionSelect: `gce-region-select-field select`,
    zoneSelect: `gce-zone-selector select`,
  };

  selectRegion(region: string) {
    this.scrollTo(CreateServerGroupModalPage.locators.regionSelect);
    browser.$(CreateServerGroupModalPage.locators.regionSelect).selectByVisibleText(region);
  }

  public selectZone(zone: string) {
    this.scrollTo(CreateServerGroupModalPage.locators.zoneSelect);
    browser.$(CreateServerGroupModalPage.locators.zoneSelect).selectByVisibleText(zone);
  }

  public addAccelerator() {
    this.scrollTo(CreateServerGroupModalPage.locators.acceleratorSectionHeading);
    this.click(CreateServerGroupModalPage.locators.addAcceleratorButton);
  }

  public getAcceleratorList(): string[] {
    this.scrollTo(CreateServerGroupModalPage.locators.acceleratorSectionHeading);
    this.click(CreateServerGroupModalPage.locators.acceleratorTypeSelect);
    browser.pause(300); // give the dropdown a moment to appear
    const accelerators = browser
      .elements(CreateServerGroupModalPage.locators.acceleratorDropdownListItems)
      .value.map((item: any) => item.getText());
    this.dismissDropdown();
    return accelerators;
  }

  public dismissDropdown() {
    this.click(CreateServerGroupModalPage.locators.createServerGroupHeading);
  }
}

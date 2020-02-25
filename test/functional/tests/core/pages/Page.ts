import '@wdio/sync';
import { defaultsDeep } from 'lodash';

const DEFAULT_OPTIONS = {
  awaitExistsTime: 10000,
};

export class Page {
  private options: { [key: string]: any };

  constructor(options = {}) {
    this.options = defaultsDeep({}, options, DEFAULT_OPTIONS);
  }

  public open(url: string): void {
    browser.url(url);
  }

  public awaitLocator(locator: string, awaitExistsTime = this.options.awaitExistsTime) {
    $(locator).waitForEnabled(awaitExistsTime);
    $(locator).waitForExist(awaitExistsTime);
    $(locator).waitForDisplayed(awaitExistsTime);
  }

  public click(locator: string) {
    this.awaitLocator(locator);
    $(locator).click();
  }

  public rightClick(locator: string) {
    this.awaitLocator(locator);
    $(locator).click({ button: 'right' });
  }

  public setInputText(locator: string, value: string) {
    this.awaitLocator(locator);
    $(locator).setValue(value);
  }

  public scrollTo(locator: string) {
    browser.execute(
      `
      var locator = arguments[0];
      var element;
      if (locator.indexOf('//') === 0) {
        var xpathResult = document.evaluate(locator, document.body);
        element = xpathResult.iterateNext();
      } else {
        element = document.querySelector(locator);
      }
      if (element == null) {
        throw new Error('Element not found for locator ' + locator);
      }
      element.scrollIntoView();
      `,
      locator,
    );
  }
}

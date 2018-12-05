import * as WebdriverIO from 'webdriverio';
import { defaultsDeep } from 'lodash';

const DEFAULT_OPTIONS = {
  awaitExistsTime: 10000,
};

export class Page {
  private options: { [key: string]: any };

  constructor(options = {}) {
    this.options = defaultsDeep({}, options, DEFAULT_OPTIONS);
  }

  public open(url: string): WebdriverIO.Client<any> {
    return browser.url(url);
  }

  public awaitLocator(locator: string) {
    browser.waitForEnabled(locator, this.options.awaitExistsTime);
    browser.waitForExist(locator, this.options.awaitExistsTime);
    browser.waitForVisible(locator, this.options.awaitExistsTime);
  }

  public click(locator: string) {
    this.awaitLocator(locator);
    browser.leftClick(locator);
  }

  public rightClick(locator: string) {
    this.awaitLocator(locator);
    browser.rightClick(locator);
  }

  public setInputText(locator: string, value: string) {
    this.awaitLocator(locator);
    browser.setValue(locator, value);
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

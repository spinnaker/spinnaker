import {module} from 'angular';

export interface INavigationPage {
  key: string;
  label: string;
  visible?: boolean;
  badge?: string;
}

export class PageNavigationState {
  public currentPageKey: string;
  public pages: INavigationPage[] = [];

  public reset(): void {
    this.pages.length = 0;
    this.currentPageKey = null;
  }

  public setCurrentPage(key: string) {
    if (this.pages.some(p => p.key === key)) {
      this.currentPageKey = key;
    }
  }

  public registerPage(page: INavigationPage) {
    this.pages.push(page);
    if (!this.currentPageKey) {
      this.currentPageKey = page.key;
    }
  }
}

export const PAGE_NAVIGATION_STATE = 'spinnaker.core.presentation.navigation.pageState';

module(PAGE_NAVIGATION_STATE, [])
  .service('pageNavigationState', PageNavigationState);

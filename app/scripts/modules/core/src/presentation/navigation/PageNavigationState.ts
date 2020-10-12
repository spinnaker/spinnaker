export interface INavigationPage {
  key: string;
  label: string;
  visible?: boolean;
  badge?: string;
}

export class PageNavigationState {
  public static currentPageKey: string;
  public static pages: INavigationPage[] = [];

  public static reset(): void {
    this.pages.length = 0;
    this.currentPageKey = null;
  }

  public static setCurrentPage(key: string) {
    if (this.pages.some((p) => p.key === key)) {
      this.currentPageKey = key;
    }
  }

  public static registerPage(page: INavigationPage) {
    this.pages.push(page);
    if (!this.currentPageKey) {
      this.currentPageKey = page.key;
    }
  }
}

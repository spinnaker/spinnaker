import { IApplicationSearchResult } from 'core/domain';
import {
  AbstractBaseResultRenderer,
  ITableColumnConfigEntry
} from 'core/search/searchResult/AbstractBaseResultRenderer';

import './application.less';

export class ApplicationDisplayRenderer extends AbstractBaseResultRenderer<IApplicationSearchResult> {

  private static instance: ApplicationDisplayRenderer = new ApplicationDisplayRenderer();

  public static renderer() {
    return ApplicationDisplayRenderer.instance;
  }

  public getRendererClass(): string {
    return 'application';
  }

  public getKey(item: IApplicationSearchResult): string {
    return item.application;
  }

  public sortItems(items: IApplicationSearchResult[]): IApplicationSearchResult[] {
    return items.sort((a, b) => a.application.localeCompare(b.application));
  }

  public getColumnConfig(): ITableColumnConfigEntry<IApplicationSearchResult>[] {
    return [
      { key: 'application', label: 'Name', cellRenderer: this.hrefCellRenderer },
      { key: 'accounts', label: 'Account', cellRenderer: this.accountCellRenderer },
      { key: 'email', label: 'Owner Email', cellRenderer: this.defaultCellRender }
    ];
  }
}

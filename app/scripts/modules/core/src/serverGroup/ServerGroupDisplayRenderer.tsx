import {
  AbstractBaseResultRenderer,
  IServerGroupSearchResult,
  ITableColumnConfigEntry
} from '../search';

import './serverGroup.less';

export class ServerGroupDisplayRenderer extends AbstractBaseResultRenderer<IServerGroupSearchResult> {

  private static instance: ServerGroupDisplayRenderer = new ServerGroupDisplayRenderer();

  public static renderer() {
    return ServerGroupDisplayRenderer.instance;
  }

  public getRendererClass(): string {
    return 'server-group';
  }

  public getKey(item: IServerGroupSearchResult): string {
    return [item.serverGroup, item.account, item.region].join('|');
  }

  public sortItems(items: IServerGroupSearchResult[]): IServerGroupSearchResult[] {
    return items.sort((a, b) => {
      let order: number = a.serverGroup.localeCompare(b.serverGroup);
      if (order === 0) {
        order = a.region.localeCompare(b.region);
      }

      return order;
    });
  }

  public getColumnConfig(): ITableColumnConfigEntry<IServerGroupSearchResult>[] {
    return [
      { key: 'serverGroup', label: 'Name', cellRenderer: this.hrefCellRenderer },
      { key: 'account', cellRenderer: this.accountCellRenderer },
      { key: 'region', cellRenderer: this.defaultCellRender },
      { key: 'email', cellRenderer: this.defaultCellRender }
    ];
  }
}

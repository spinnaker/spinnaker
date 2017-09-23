import {
  AbstractBaseResultRenderer,
  IClusterSearchResult,
  ITableColumnConfigEntry
} from '../search';

import './cluster.less';

export class ClusterDisplayRenderer extends AbstractBaseResultRenderer<IClusterSearchResult> {

  private static instance: ClusterDisplayRenderer = new ClusterDisplayRenderer();

  public static renderer() {
    return ClusterDisplayRenderer.instance;
  }

  public getRendererClass(): string {
    return 'cluster';
  }

  public getKey(item: IClusterSearchResult): string {
    return [item.cluster, item.account].join('|');
  }

  public sortItems(items: IClusterSearchResult[]): IClusterSearchResult[] {
    return items.sort((a, b) => a.cluster.localeCompare(b.cluster));
  }

  public getColumnConfig(): ITableColumnConfigEntry<IClusterSearchResult>[] {
    return [
      { key: 'cluster', label: 'Name', cellRenderer: this.hrefCellRenderer },
      { key: 'account', cellRenderer: this.accountCellRenderer },
      { key: 'email', cellRenderer: this.defaultCellRender }
    ];
  }
}

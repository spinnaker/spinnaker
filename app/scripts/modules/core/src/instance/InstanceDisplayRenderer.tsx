import {
  AbstractBaseResultRenderer,
  IInstanceSearchResult,
  ITableColumnConfigEntry
} from '../search';

import './instance.less';

export class InstanceDisplayRenderer extends AbstractBaseResultRenderer<IInstanceSearchResult> {

  private static instance: InstanceDisplayRenderer = new InstanceDisplayRenderer();

  public static renderer() {
    return InstanceDisplayRenderer.instance;
  }

  public getRendererClass(): string {
    return 'instance';
  }

  public getKey(item: IInstanceSearchResult): string {
    return item.instanceId;
  }

  public sortItems(items: IInstanceSearchResult[]): IInstanceSearchResult[] {
    return items.sort((a, b) => a.instanceId.localeCompare(b.instanceId));
  }

  public getColumnConfig(): ITableColumnConfigEntry<IInstanceSearchResult>[] {
    return [
      { key: 'instanceId', label: 'Instance ID', cellRenderer: this.hrefCellRenderer },
      { key: 'account', cellRenderer: this.accountCellRenderer },
      { key: 'region', cellRenderer: this.defaultCellRender },
      { key: 'serverGroup', label: 'Server Group', defaultValue: 'Standalone Instance', cellRenderer: this.valueOrDefaultCellRenderer }
    ];
  }
}

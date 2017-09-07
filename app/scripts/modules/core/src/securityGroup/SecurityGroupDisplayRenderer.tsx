import {
  AbstractBaseResultRenderer,
  ISecurityGroupSearchResult,
  ITableColumnConfigEntry
} from '../search';

import './securityGroup.less';

export class SecurityGroupDisplayRenderer extends AbstractBaseResultRenderer<ISecurityGroupSearchResult> {

  private static instance: SecurityGroupDisplayRenderer = new SecurityGroupDisplayRenderer();

  public static renderer() {
    return SecurityGroupDisplayRenderer.instance;
  }

  public getRendererClass(): string {
    return 'security-group';
  }

  public getKey(item: ISecurityGroupSearchResult): string {
    return [item.id, item.name, item.account, item.region].join('|');
  }

  public sortItems(items: ISecurityGroupSearchResult[]): ISecurityGroupSearchResult[] {
    return items.sort((a, b) => {
      let order: number = a.name.localeCompare(b.name);
      if (order === 0) {
        order = a.region.localeCompare(b.region);
      }

      return order;
    });
  }

  public getColumnConfig(): ITableColumnConfigEntry<ISecurityGroupSearchResult>[] {
    return [
      { key: 'name', cellRenderer: this.hrefCellRenderer },
      { key: 'account', cellRenderer: this.accountCellRenderer },
      { key: 'region', cellRenderer: this.defaultCellRender }
    ];
  }
}

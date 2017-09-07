import {
  AbstractBaseResultRenderer,
  ILoadBalancerSearchResult,
  ITableColumnConfigEntry
} from '../search';

import './loadBalancer.less';

export class LoadBalancerDisplayRenderer extends AbstractBaseResultRenderer<ILoadBalancerSearchResult> {

  private static instance: LoadBalancerDisplayRenderer = new LoadBalancerDisplayRenderer();

  public static renderer() {
    return LoadBalancerDisplayRenderer.instance;
  }

  public getRendererClass(): string {
    return 'load-balancer';
  }

  public getKey(item: ILoadBalancerSearchResult): string {
    return [item.loadBalancer, item.account, item.region].join('|');
  }

  public sortItems(items: ILoadBalancerSearchResult[]): ILoadBalancerSearchResult[] {
    return items.sort((a, b) => {
      let order: number = a.loadBalancer.localeCompare(b.loadBalancer);
      if (order === 0) {
        order = a.region.localeCompare(b.region);
      }

      return order;
    });
  }

  public getColumnConfig(): ITableColumnConfigEntry<ILoadBalancerSearchResult>[] {
    return [
      { key: 'loadBalancer', label: 'Name', cellRenderer: this.hrefCellRenderer },
      { key: 'account', cellRenderer: this.accountCellRenderer },
      { key: 'region', cellRenderer: this.defaultCellRender },
      { key: 'loadBalancerType', label: 'Type', cellRenderer: this.defaultCellRender }
    ];
  }
}

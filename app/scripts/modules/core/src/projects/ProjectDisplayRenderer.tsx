import {
  AbstractBaseResultRenderer,
  IProjectSearchResult,
  ITableColumnConfigEntry
} from '../search';

import './project.less';

export class ProjectDisplayRenderer extends AbstractBaseResultRenderer<IProjectSearchResult> {

  private static instance: ProjectDisplayRenderer = new ProjectDisplayRenderer();

  public static renderer() {
    return ProjectDisplayRenderer.instance;
  }

  public getRendererClass(): string {
    return 'project';
  }

  public getKey(item: IProjectSearchResult): string {
    return item.id;
  }

  public sortItems(items: IProjectSearchResult[]): IProjectSearchResult[] {
    return items.sort((a, b) => a.name.localeCompare(b.name));
  }

  public getColumnConfig(): ITableColumnConfigEntry<IProjectSearchResult>[] {
    return [
      { key: 'name', cellRenderer: this.hrefCellRenderer },
      { key: 'email', cellRenderer: this.defaultCellRender }
    ];
  }
}

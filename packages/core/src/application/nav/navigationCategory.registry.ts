import { cloneDeep } from 'lodash';

import { IconNames } from '../../presentation';

export interface INavigationCategory {
  key: string;
  label: string;
  icon?: string;
  iconName?: IconNames;
  primary: boolean;
  order: number;
}

export class NavigationCategoryRegistry {
  private categories: INavigationCategory[] = [];
  private orderOverrides: Map<string, number> = new Map();

  public register(category: INavigationCategory): void {
    this.categories.push(category);
  }

  public get(key: string): INavigationCategory {
    return cloneDeep(this.categories.find((c) => c.key === key));
  }

  public has(key: string): boolean {
    return this.categories.some((c) => c.key === key);
  }

  public getAll(): INavigationCategory[] {
    return this.categories.slice().sort((a, b) => this.getOrder(a) - this.getOrder(b));
  }

  public overrideCategoryOrder(key: string, order: number): void {
    this.orderOverrides.set(key, order);
  }

  public getHighestOrder(): number {
    return Math.max(...this.categories.map((c) => c.order).concat(Array.from(this.orderOverrides.values())));
  }

  public getOrder(category: INavigationCategory): number {
    const { key, order } = category;
    return this.orderOverrides.has(key) ? this.orderOverrides.get(key) : order;
  }
}

export const navigationCategoryRegistry = new NavigationCategoryRegistry();

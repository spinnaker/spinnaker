import { Ng1StateDeclaration } from '@uirouter/angularjs';
import { module } from 'angular';
import { find, isUndefined, omit, omitBy, sortBy } from 'lodash';
import { Duration } from 'luxon';

import { DeckCacheFactory, ICache } from '../cache';
import { UUIDGenerator } from '../utils/uuid.service';
import IAngularEvent = angular.IAngularEvent;

export interface ICacheEntryStateMigrator {
  // a string literal in the state to be replaced (not a regex)
  state: string;
  // string to replace in the state (also not a regex)
  replacement: string;
}

export interface IRecentHistoryEntry {
  params: any;
  state: string;
  accessTime: number;
  extraData: any;
  id: string;
}

const MAX_ITEMS = 5;

export class RecentHistoryService {
  private static cache: ICache = DeckCacheFactory.createCache('history', 'user', {
    version: 3,
    maxAge: Duration.fromObject({ days: 90 }).as('milliseconds'),
  });

  public static getItems(type: any): IRecentHistoryEntry[] {
    const replacements: ICacheEntryStateMigrator[] = [
      {
        state: 'securityGroup',
        replacement: 'firewall',
      },
      {
        state: 'securityGroupDetails',
        replacement: 'firewallDetails',
      },
      // example: replace "application.executions" with "application.pipelines.executions"
      // {
      //   state: 'application.executions',
      //   replacement: 'application.pipelines.executions'
      // },
    ];
    const items: IRecentHistoryEntry[] = this.cache.get(type) || [];
    items.forEach((item) => {
      replacements.forEach((replacement) => {
        if (item.state && item.state.includes(replacement.state)) {
          item.state = item.state.replace(replacement.state, replacement.replacement);
        }
      });
    });
    return sortBy(items, 'accessTime').reverse();
  }

  public static addItem(type: string, state: string, params: any, keyParams: string[] = []) {
    const items: IRecentHistoryEntry[] = this.getItems(type).slice(0, MAX_ITEMS);
    const existing: IRecentHistoryEntry = this.getExisting(items, params, keyParams);
    const entry = {
      params,
      state,
      accessTime: new Date().getTime(),
      extraData: {},
      id: UUIDGenerator.generateUuid(),
    };
    if (existing) {
      items.splice(items.indexOf(existing), 1);
    }
    if (items.length === MAX_ITEMS) {
      items.pop();
    }
    items.push(entry);
    this.cache.put(type, items);
  }

  public static removeItem(type: string, id: string): void {
    const items: IRecentHistoryEntry[] = this.getItems(type);
    const existing: IRecentHistoryEntry = items.find((i) => i.id === id);

    if (existing) {
      items.splice(items.indexOf(existing), 1);
      this.cache.put(type, items);
    }
  }

  public static removeLastItem(type: string): void {
    const items: IRecentHistoryEntry[] = this.getItems(type);
    if (items.length) {
      items.splice(0, 1);
      this.cache.put(type, items);
    }
  }

  /**
   * Used to include additional fields needed by display formatters that might not be present in $stateParams,
   * but are resolved in a controller when the view loads
   * See instanceDetails.controller.js for an example
   * @param type
   * @param extraData
   */
  public static addExtraDataToLatest(type: string, extraData: any): void {
    const items: IRecentHistoryEntry[] = this.getItems(type);
    if (items.length) {
      items[0].extraData = extraData;
      this.cache.put(type, items);
    }
  }

  /**
   * Called when deleting an application to remove its record in recent history
   * @param appName
   */
  public static removeByAppName(appName: string) {
    const type = 'applications';
    const items: IRecentHistoryEntry[] = this.getItems(type);
    const remaining: IRecentHistoryEntry[] = items.filter((item) => item.params.application !== appName);
    if (remaining) {
      this.cache.put(type, remaining);
    }
  }

  private static getExisting(items: IRecentHistoryEntry[], params: any, keyParams: string[]): IRecentHistoryEntry {
    if (!keyParams || !keyParams.length) {
      return find(items, { params: omitBy(params, isUndefined) });
    }
    return items.find((item) => keyParams.every((p) => item.params[p] === params[p]));
  }
}

export const RECENT_HISTORY_SERVICE = 'spinnaker.core.history.recentHistory.service';
module(RECENT_HISTORY_SERVICE, []).run([
  '$rootScope',
  ($rootScope: ng.IRootScopeService) => {
    $rootScope.$on('$stateChangeSuccess', (_event: IAngularEvent, toState: Ng1StateDeclaration, toParams: any) => {
      const history = toState.data?.history;
      if (history) {
        const params = omit(toParams || {}, ['debug', 'vis', 'trace']);
        const state = history.state || toState.name;
        RecentHistoryService.addItem(history.type, state, params, history.keyParams);
      }
    });
  },
]);

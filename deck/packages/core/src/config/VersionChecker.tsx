import { $http, $log } from 'ngimport';
import React from 'react';
// @ts-ignore
import version from 'root/version.json';

import type { IScheduler } from '../scheduler/SchedulerFactory';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';
import { timestamp } from '../utils/timeFormatters';
import { NotifierService } from '../widgets/notifier/notifier.service';

export interface IDeckVersion {
  version: string;
  created: number;
}

export class VersionChecker {
  private static currentVersion: IDeckVersion = version;
  private static newVersionSeenCount = 0;
  private static scheduler: IScheduler;

  public static initialize(): void {
    $log.debug('Deck version', this.currentVersion.version, 'created', timestamp(this.currentVersion.created));
    this.scheduler = SchedulerFactory.createScheduler();
    this.scheduler.subscribe(() => this.checkVersion());
  }

  private static checkVersion(): void {
    const url = `/version.json?_=${Date.now()}`;
    $http
      .get(url)
      .then((resp) => this.versionRetrieved(resp))
      .catch(() => {});
  }

  private static versionRetrieved(response: any): void {
    const data: IDeckVersion = response.data;
    if (data.version === this.currentVersion.version) {
      this.newVersionSeenCount = 0;
    } else {
      this.newVersionSeenCount++;
      if (this.newVersionSeenCount > 5) {
        $log.debug('New Deck version:', data.version, 'created', timestamp(data.created));
        NotifierService.publish({
          key: 'newVersion',
          action: 'create',
          content: (
            <div>
              A new version of Spinnaker is available{' '}
              <a role="button" className="action" onClick={() => document.location.reload()}>
                Refresh
              </a>
            </div>
          ),
        });
        this.scheduler.unsubscribe();
      }
    }
  }
}

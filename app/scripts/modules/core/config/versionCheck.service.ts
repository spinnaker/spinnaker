import {module} from 'angular';

import {SETTINGS} from 'core/config/settings';

interface IDeckVersion {
  version: string;
  created: number;
}

class VersionCheckService {
  private currentVersion: IDeckVersion = require('../../../../../version.json');
  private newVersionSeenCount = 0;
  private scheduler: any;

  static get $inject(): string[] {
    return ['$http', 'notifierService', 'schedulerFactory', '$log', '$filter'];
  }

  constructor(private $http: ng.IHttpService,
              private notifierService: any,
              private schedulerFactory: any,
              private $log: ng.ILogService,
              private $filter: any) {}

  public initialize(): void {
    this.$log.debug(
      'Deck version',
      this.currentVersion.version,
      'created',
      this.$filter('timestamp')(this.currentVersion.created)
    );
    this.scheduler = this.schedulerFactory.createScheduler();
    this.scheduler.subscribe(() => this.checkVersion());
  }

  private checkVersion(): void {
    const url = `/version.json?_=${Date.now()}`;
    this.$http.get(url).then((resp) => this.versionRetrieved(resp));
  }

  private versionRetrieved(response: any): void {
    const data: IDeckVersion = response.data;
    if (data.version === this.currentVersion.version) {
      this.newVersionSeenCount = 0;
    } else {
      this.newVersionSeenCount++;
      if (this.newVersionSeenCount > 5) {
        this.$log.debug('New Deck version:', data.version, 'created', this.$filter('timestamp')(data.created));
        this.notifierService.publish({
          key: 'newVersion',
          position: 'bottom',
          body: `A new version of Spinnaker is available
              <a role="button" class="action" onclick="document.location.reload(true)">Refresh</a>`
        });
        this.scheduler.unsubscribe();
      }
    }
  }
}

export const VERSION_CHECK_SERVICE = 'spinnaker.core.config.versionCheck.service';
module(VERSION_CHECK_SERVICE, [
  require('../widgets/notifier/notifier.service'),
  require('core/scheduler/scheduler.factory'),
]).service('versionCheckService', VersionCheckService)
  .run((versionCheckService: VersionCheckService) => {
    if (SETTINGS.checkForUpdates) {
      versionCheckService.initialize();
    }
  });

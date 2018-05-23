import { module } from 'angular';

import { Application } from 'core/application/application.model';
import { NotifierService } from 'core/widgets/notifier/notifier.service';

export class InferredApplicationWarningService {
  private viewedApplications: string[] = [];

  public resetViewedApplications(): void {
    this.viewedApplications.length = 0;
  }

  public isInferredApplication(app: Application): boolean {
    return !app.attributes || !app.attributes.email;
  }

  public checkIfInferredAndWarn(app: Application): void {
    if (this.check(app)) {
      this.warn(app.name);
    }
  }

  private check(app: Application): boolean {
    const { name } = app;
    const hasViewed = this.viewedApplications.includes(name);

    this.viewedApplications.push(name);
    return !hasViewed && this.isInferredApplication(app);
  }

  private warn(appName: string): void {
    NotifierService.publish({
      key: 'inferredApplicationWarning',
      action: 'create',
      body: `The application <b>${appName}</b> has not been <a href="#/applications/${appName}/config">configured</a>.`,
    });
  }
}

export const INFERRED_APPLICATION_WARNING_SERVICE = 'spinnaker.core.application.inferredApplicationWarning.service';
module(INFERRED_APPLICATION_WARNING_SERVICE, []).service(
  'inferredApplicationWarningService',
  InferredApplicationWarningService,
);

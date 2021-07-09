import { Application } from '../application';
import { ModalInjector } from '../reactShims';
import { IJob, ITaskCommand, TaskExecutor } from '../task/taskExecutor';

export class PagerDutyWriter {
  public static pageApplicationOwnerModal(app: Application): void {
    ModalInjector.modalService
      .open({
        templateUrl: require('./pageApplicationOwner.html'),
        controller: 'PageModalCtrl as ctrl',
        resolve: {
          application: () => app,
        },
      })
      .result.catch(() => {});
  }

  public static sendPage(
    applications: Application[],
    keys: string[],
    reason: string,
    ownerApp: Application,
    details?: { [key: string]: any },
  ): PromiseLike<any> {
    const job = {
      type: 'pageApplicationOwner',
      message: reason,
      details,
    } as IJob;

    if (applications && applications.length > 0) {
      job.applications = applications.map((app) => app.name);
    }

    if (keys && keys.length > 0) {
      job.keys = keys;
    }

    const task = {
      application: ownerApp,
      job: [job],
      description: 'Send Page',
    } as ITaskCommand;

    return TaskExecutor.executeTask(task);
  }

  public static pageApplicationOwner(application: Application, reason: string, details?: string): PromiseLike<any> {
    return this.sendPage([application], undefined, reason, application, { details });
  }
}

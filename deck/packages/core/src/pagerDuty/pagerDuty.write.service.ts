import type { Application } from '../application';
import { ConfirmationModalService } from '../confirmationModal/confirmationModal.service';
import type { IJob, ITaskCommand } from '../task/taskExecutor';
import { TaskExecutor } from '../task/taskExecutor';

export class PagerDutyWriter {
  public static pageApplicationOwnerModal(application: Application): PromiseLike<any> {
    return ConfirmationModalService.confirm({
      header: `Page ${application.name} Owner`,
      buttonText: 'Page Owner',
      askForReason: true,
      reasonRequired: true,
      reasonPlaceholder: 'Why is the owner being paged?',
      taskMonitorConfig: {
        application,
        title: `Paging ${application.name} owner`,
      },
      submitMethod: ({ reason }: { reason: string }) =>
        this.pageApplicationOwner(application, `[${application.name.toUpperCase()}] ${reason.trim()}`),
    });
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

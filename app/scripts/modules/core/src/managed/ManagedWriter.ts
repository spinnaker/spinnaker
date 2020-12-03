import { REST } from 'core/api';
import { StatefulConstraintStatus } from 'core/domain';

export interface IArtifactVersionRequest {
  application: string;
  environment: string;
  reference: string;
  version: string;
  comment: string;
}

export interface IUnpinArtifactVersionRequest {
  application: string;
  environment: string;
  reference: string;
}

export interface IUpdateConstraintStatusRequest {
  application: string;
  environment: string;
  type: string;
  reference: string;
  version: string;
  status: StatefulConstraintStatus;
}

export class ManagedWriter {
  public static pinArtifactVersion({
    application,
    environment,
    reference,
    version,
    comment,
  }: IArtifactVersionRequest): PromiseLike<void> {
    return REST().path('managed', 'application', application, 'pin').post({
      targetEnvironment: environment,
      reference,
      version,
      comment,
    });
  }

  public static unpinArtifactVersion({
    application,
    environment,
    reference,
  }: IUnpinArtifactVersionRequest): PromiseLike<void> {
    return REST().path('managed', 'application', application, 'pin', environment).query({ reference }).delete();
  }

  public static markArtifactVersionAsBad({
    application,
    environment,
    reference,
    version,
    comment,
  }: IArtifactVersionRequest): PromiseLike<void> {
    return REST().path('managed', 'application', application, 'veto').post({
      targetEnvironment: environment,
      reference,
      version,
      comment,
    });
  }

  public static updateConstraintStatus({
    application,
    environment,
    type,
    reference,
    version,
    status,
  }: IUpdateConstraintStatusRequest): PromiseLike<void> {
    return REST().path('managed', 'application', application, 'environment', environment, 'constraint').post({
      type,
      artifactReference: reference,
      artifactVersion: version,
      status,
    });
  }

  public static pauseApplicationManagement(applicationName: string): PromiseLike<void> {
    return REST().path('managed', 'application', applicationName, 'pause').post();
  }

  public static resumeApplicationManagement(applicationName: string): PromiseLike<void> {
    return REST().path('managed', 'application', applicationName, 'pause').delete();
  }

  public static pauseResourceManagement(resourceId: string): PromiseLike<void> {
    return REST().path('managed', 'resources', resourceId, 'pause').post();
  }

  public static resumeResourceManagement(resourceId: string): PromiseLike<void> {
    return REST().path('managed', 'resources', resourceId, 'pause').delete();
  }
}

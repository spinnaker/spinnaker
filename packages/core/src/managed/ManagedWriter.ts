import { REST } from '../api';
import { ConstraintStatus } from '../domain';

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
  status: ConstraintStatus;
}

export class ManagedWriter {
  public static pinArtifactVersion({
    application,
    environment,
    reference,
    version,
    comment,
  }: IArtifactVersionRequest): PromiseLike<void> {
    return REST('/managed/application').path(application, 'pin').post({
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
    return REST('/managed/application').path(application, 'pin', environment).query({ reference }).delete();
  }

  public static markArtifactVersionAsBad({
    application,
    environment,
    reference,
    version,
    comment,
  }: IArtifactVersionRequest): PromiseLike<void> {
    return REST('/managed/application').path(application, 'veto').post({
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
    return REST('/managed/application').path(application, 'environment', environment, 'constraint').post({
      type,
      artifactReference: reference,
      artifactVersion: version,
      status,
    });
  }

  public static pauseApplicationManagement(applicationName: string): PromiseLike<void> {
    return REST('/managed/application').path(applicationName, 'pause').post();
  }

  public static resumeApplicationManagement(applicationName: string): PromiseLike<void> {
    return REST('/managed/application').path(applicationName, 'pause').delete();
  }

  public static pauseResourceManagement(resourceId: string): PromiseLike<void> {
    return REST('/managed/resources').path(resourceId, 'pause').post();
  }

  public static resumeResourceManagement(resourceId: string): PromiseLike<void> {
    return REST('/managed/resources').path(resourceId, 'pause').delete();
  }
}

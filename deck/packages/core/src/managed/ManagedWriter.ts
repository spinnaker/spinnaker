import { REST } from '../api';
export class ManagedWriter {
  public static pauseResourceManagement(resourceId: string): PromiseLike<void> {
    return REST('/managed/resources').path(resourceId, 'pause').post();
  }

  public static resumeResourceManagement(resourceId: string): PromiseLike<void> {
    return REST('/managed/resources').path(resourceId, 'pause').delete();
  }
}

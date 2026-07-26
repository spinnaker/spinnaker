import { REST } from '../api';
export class ManagedWriter {
  public static pauseResourceManagement(resourceId: string): Promise<void> {
    return REST('/managed/resources').path(resourceId, 'pause').post();
  }

  public static resumeResourceManagement(resourceId: string): Promise<void> {
    return REST('/managed/resources').path(resourceId, 'pause').delete();
  }
}

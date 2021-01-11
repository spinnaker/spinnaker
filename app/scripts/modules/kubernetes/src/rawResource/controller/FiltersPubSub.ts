import { IK8sResourcesFiltersState } from '../component/K8sResourcesFilters';

type ISubscriber = (message: IK8sResourcesFiltersState) => void;

export class FiltersPubSub {
  private subscribers: ISubscriber[] = [];
  private static intances: Record<string, FiltersPubSub> = {};

  private constructor() {}

  public static getInstance(name: string) {
    let instance = FiltersPubSub.intances[name];
    if (!FiltersPubSub.intances[name]) {
      FiltersPubSub.intances[name] = new FiltersPubSub();
      instance = FiltersPubSub.intances[name];
    }
    return instance;
  }

  public subscribe(subscriber: ISubscriber) {
    this.subscribers.push(subscriber);
    return () => (this.subscribers = this.subscribers.filter((x) => x !== subscriber));
  }

  public unsubscribe(subscriber: ISubscriber) {
    const index = this.subscribers.indexOf(subscriber);
    if (index > -1) {
      this.subscribers.splice(index, 1);
    }
  }

  public publish(message: IK8sResourcesFiltersState) {
    this.subscribers.forEach((sub) => sub(message));
  }
}

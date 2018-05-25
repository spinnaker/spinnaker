export interface IAppNotification {
  level: string;
  type: string;
  when: string[];
  [key: string]: any;
}

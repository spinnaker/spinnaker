export interface IRestrictedExecutionWindow {
  whitelist: IWindow[];
  days: number[];
  jitter: IJitter;
  atlasEnabled?: boolean;
  currentRegion?: string;
}

export interface IWindow {
  startMin: number;
  startHour: number;
  endMin: number;
  endHour: number;
  wrapEnd?: boolean;
}

export interface ITimelineWindow {
  style: any;
  start: Date;
  end: Date;
  displayStart: Date;
  displayEnd: Date;
}

export interface IJitter {
  minDelay: number;
  maxDelay: number;
  enabled: boolean;
  skipManual: boolean;
}

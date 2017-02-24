export interface IStage {
  name: string;
  type: string;
  refId: string | number; // unfortunately, we kept this loose early on, so it's either a string or a number
  requisiteStageRefIds: (string | number)[];
  [k: string]: any;
  isNew?: boolean;
}

export interface IStageSummary extends IStage {
  masterStage: IStage;
  labelTemplateUrl: string;
  extraLabelLines?: (stage: IStageSummary) => number;
  index: number;
  status: string;
  hasNotStarted: boolean;
}

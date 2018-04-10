export interface IStage {
  [k: string]: any;
  alias?: string;
  group?: string;
  isNew?: boolean;
  name: string;
  refId: string | number; // unfortunately, we kept this loose early on, so it's either a string or a number
  requisiteStageRefIds: Array<string | number>;
  type: string;
}

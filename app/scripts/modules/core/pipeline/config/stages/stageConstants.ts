export interface IStageConstant {
  description: string;
  label: string;
  val: string;
}

export class StageConstants {

  public static TARGET_LIST: IStageConstant[] =
    Object.freeze([
      Object.freeze({
        label: 'Newest Server Group',
        val: 'current_asg_dynamic',
        description: 'Selects the most recently deployed server group when this <b>stage</b> starts.'
      }),
      Object.freeze({
        label: 'Previous Server Group',
        val: 'ancestor_asg_dynamic',
        description: 'Selects the second-most recently deployed server group when this <b>stage</b> starts.'
      }),
      Object.freeze({
        label: 'Oldest Server Group',
        val: 'oldest_asg_dynamic',
        description: 'Selects the least recently deployed server group when this <b>stage</b> starts.'
      }),
      Object.freeze({
        label: '(Deprecated) Current Server Group',
        val: 'current_asg',
        description: 'Selects the most recently deployed server group when the <b>pipeline</b> starts, ignoring any changes ' +
        'to the state of the cluster caused by upstream stages.'
      }),
      Object.freeze({
        label: '(Deprecated) Last Server Group',
        val: 'ancestor_asg',
        description: 'Selects the second-most recently deployed server group when the <b>pipeline</b> starts, ignoring any changes ' +
        'to the state of the cluster caused by upstream stages.'
      })
    ]);

  public static STRATEGY_TRAFFIC_OPTIONS: IStageConstant[] =
    Object.freeze([
      Object.freeze({
        label: 'From Cluster Configuration',
        val: 'inherit',
        description: 'Traffic options are set in the advanced options tab of the deploy stage that calls the strategy'
      }),
      Object.freeze({
        label: 'Always Enable',
        description: 'Always send client requests to new instances',
        val: 'enable'
      }),
      Object.freeze({
        label: 'Always Disable',
        description: 'Never send client requests to new instances',
        val: 'disable'
      })
    ]);
}

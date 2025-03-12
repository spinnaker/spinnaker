export interface IStageConstant {
  description: string;
  label: string;
  val: string;
}

export class StageConstants {
  public static get TARGET_LIST(): IStageConstant[] {
    return StageConstants.targetList;
  }

  public static get STRATEGY_TRAFFIC_OPTIONS(): IStageConstant[] {
    return StageConstants.strategyTrafficOptions;
  }

  public static get IMAGE_PRODUCING_STAGES(): string[] {
    return ['bake', 'findImageFromTags', 'findImage', 'findAmi'];
  }

  public static get MANIFEST_CRITERIA_OPTIONS(): IStageConstant[] {
    return StageConstants.manifestCriteriaOptions;
  }

  private static targetList: IStageConstant[] = [
    {
      label: 'Newest Server Group',
      val: 'current_asg_dynamic',
      description: 'Selects the most recently deployed server group when this <b>stage</b> starts.',
    },
    {
      label: 'Previous Server Group',
      val: 'ancestor_asg_dynamic',
      description: 'Selects the second-most recently deployed server group when this <b>stage</b> starts.',
    },
    {
      label: 'Oldest Server Group',
      val: 'oldest_asg_dynamic',
      description: 'Selects the least recently deployed server group when this <b>stage</b> starts.',
    },
    {
      label: '(Deprecated) Current Server Group',
      val: 'current_asg',
      description:
        'Selects the most recently deployed server group when the <b>pipeline</b> starts, ignoring any changes ' +
        'to the state of the cluster caused by upstream stages.',
    },
    {
      label: '(Deprecated) Last Server Group',
      val: 'ancestor_asg',
      description:
        'Selects the second-most recently deployed server group when the <b>pipeline</b> starts, ignoring any changes ' +
        'to the state of the cluster caused by upstream stages.',
    },
  ];

  private static strategyTrafficOptions: IStageConstant[] = [
    {
      label: 'From Cluster Configuration',
      val: 'inherit',
      description: 'Traffic options are set in the advanced options tab of the deploy stage that calls the strategy',
    },
    {
      label: 'Always Enable',
      description: 'Always send client requests to new instances',
      val: 'enable',
    },
    {
      label: 'Always Disable',
      description: 'Never send client requests to new instances',
      val: 'disable',
    },
  ];

  private static manifestCriteriaOptions: IStageConstant[] = [
    {
      label: 'Newest',
      val: 'newest',
      description: 'Selects the most recently deployed manifest in the cluster when this <b>stage</b> starts.',
    },
    {
      label: 'Second Newest',
      val: 'second_newest',
      description: 'Selects the second-most recently deployed manifest in the cluster when this <b>stage</b> starts.',
    },
    {
      label: 'Oldest',
      val: 'oldest',
      description: 'Selects the least recently deployed manifest in the cluster when this <b>stage</b> starts.',
    },
    {
      label: 'Largest',
      val: 'largest',
      description: 'Selects the largest (by number of pods) manifest in the cluster when this <b>stage</b> starts.',
    },
    {
      label: 'Smallest',
      val: 'smallest',
      description: 'Selects the smallest (by number of pods) manifest in the cluster when this <b>stage</b> starts.',
    },
  ];
}

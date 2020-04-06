import { ReactComponent as accordionCollapse } from './vectors/accordionCollapse.svg';
import { ReactComponent as accordionExpand } from './vectors/accordionExpand.svg';
import { ReactComponent as accordionExpandAll } from './vectors/accordionExpandAll.svg';
import { ReactComponent as artifact } from './vectors/artifact.svg';
import { ReactComponent as build } from './vectors/build.svg';
import { ReactComponent as caretRight } from './vectors/caretRight.svg';
import { ReactComponent as checkBadge } from './vectors/checkBadge.svg';
import { ReactComponent as checkboxIndeterminate } from './vectors/checkboxIndeterminate.svg';
import { ReactComponent as checkboxChecked } from './vectors/checkboxChecked.svg';
import { ReactComponent as checkboxUnchecked } from './vectors/checkboxUnchecked.svg';
import { ReactComponent as close } from './vectors/close.svg';
import { ReactComponent as closeSmall } from './vectors/closeSmall.svg';
import { ReactComponent as cloudDeployed } from './vectors/cloudDeployed.svg';
import { ReactComponent as cloudError } from './vectors/cloudError.svg';
import { ReactComponent as cloudProgress } from './vectors/cloudProgress.svg';
import { ReactComponent as cloudDecommissioned } from './vectors/cloudDecommissioned.svg';
import { ReactComponent as cluster } from './vectors/cluster.svg';
import { ReactComponent as copyClipboard } from './vectors/copyClipboard.svg';
import { ReactComponent as environment } from './vectors/environment.svg';
import { ReactComponent as fn } from './vectors/fn.svg';
import { ReactComponent as fnNew } from './vectors/fnNew.svg';
import { ReactComponent as instances } from './vectors/instances.svg';
import { ReactComponent as loadBalancer } from './vectors/loadBalancer.svg';
import { ReactComponent as manualJudgement } from './vectors/manualJudgement.svg';
import { ReactComponent as placeholder } from './vectors/placeholder.svg';
import { ReactComponent as securityGroup } from './vectors/securityGroup.svg';
import { ReactComponent as servergroupAws } from './vectors/servergroupAws.svg';
import { ReactComponent as spel } from './vectors/spel.svg';
import { ReactComponent as templateFull } from './vectors/templateFull.svg';
import { ReactComponent as templateWorkflow } from './vectors/templateWorkflow.svg';

// Icons prefixed sp* are intended for use with the current Spinnaker UI design. They are visually heavier.
import { ReactComponent as spCIBranch } from './vectors/spCIBranch.svg';
import { ReactComponent as spCIBuild } from './vectors/spCIBuild.svg';
import { ReactComponent as spCICommit } from './vectors/spCICommit.svg';
import { ReactComponent as spCIMaster } from './vectors/spCIMaster.svg';
import { ReactComponent as spCIPullRequest } from './vectors/spCIPullRequest.svg';
import { ReactComponent as spMenuCanaryConfig } from './vectors/spMenuCanaryConfig.svg';
import { ReactComponent as spMenuCanaryReport } from './vectors/spMenuCanaryReport.svg';
import { ReactComponent as spMenuClusters } from './vectors/spMenuClusters.svg';
import { ReactComponent as spMenuConfig } from './vectors/spMenuConfig.svg';
import { ReactComponent as spMenuLoadBalancers } from './vectors/spMenuLoadBalancers.svg';
import { ReactComponent as spMenuPager } from './vectors/spMenuPager.svg';
import { ReactComponent as spMenuPipelines } from './vectors/spMenuPipelines.svg';
import { ReactComponent as spMenuProperties } from './vectors/spMenuProperties.svg';
import { ReactComponent as spMenuSecurityGroups } from './vectors/spMenuSecurityGroups.svg';
import { ReactComponent as spMenuTasks } from './vectors/spMenuTasks.svg';
import { ReactComponent as spMenuTimeline } from './vectors/spMenuTimeline.svg';

// Kayenta
import { ReactComponent as canaryFail } from './vectors/canaryFail.svg';
import { ReactComponent as canaryRunning } from './vectors/canaryRunning.svg';
import { ReactComponent as canaryPass } from './vectors/canaryPass.svg';
import { ReactComponent as canaryMarginal } from './vectors/canaryMarginal.svg';

// Managed Delivery
import { ReactComponent as mdActuating } from './vectors/mdActuating.svg';
import { ReactComponent as mdActuationLaunched } from './vectors/mdActuationLaunched.svg';
import { ReactComponent as mdCreated } from './vectors/mdCreated.svg';
import { ReactComponent as mdDeltaDetected } from './vectors/mdDeltaDetected.svg';
import { ReactComponent as mdDeltaResolved } from './vectors/mdDeltaResolved.svg';
import { ReactComponent as mdDiff } from './vectors/mdDiff.svg';
import { ReactComponent as mdError } from './vectors/mdError.svg';
import { ReactComponent as mdFlapping } from './vectors/mdFlapping.svg';
import { ReactComponent as mdPaused } from './vectors/mdPaused.svg';
import { ReactComponent as mdResumed } from './vectors/mdResumed.svg';
import { ReactComponent as mdUnknown } from './vectors/mdUnknown.svg';
import { ReactComponent as mdConstraintGeneric } from './vectors/mdConstraintGeneric.svg';
import { ReactComponent as mdConstraintDependsOn } from './vectors/mdConstraintDependsOn.svg';
import { ReactComponent as mdConstraintAllowedTimes } from './vectors/mdConstraintAllowedTimes.svg';
import { ReactComponent as md } from './vectors/md.svg';

export const iconsByName = {
  accordionCollapse,
  accordionExpand,
  accordionExpandAll,
  artifact,
  build,
  canaryFail,
  canaryRunning,
  canaryPass,
  canaryMarginal,
  caretRight,
  checkBadge,
  checkboxIndeterminate,
  checkboxChecked,
  checkboxUnchecked,
  close,
  closeSmall,
  cloudDeployed,
  cloudError,
  cloudProgress,
  cloudDecommissioned,
  cluster,
  copyClipboard,
  environment,
  fn,
  fnNew,
  instances,
  loadBalancer,
  manualJudgement,
  mdActuating,
  mdActuationLaunched,
  mdCreated,
  mdDeltaDetected,
  mdDeltaResolved,
  mdDiff,
  mdError,
  mdFlapping,
  mdPaused,
  mdResumed,
  mdUnknown,
  mdConstraintGeneric,
  mdConstraintDependsOn,
  mdConstraintAllowedTimes,
  md,
  placeholder,
  securityGroup,
  servergroupAws,
  spCIBranch,
  spCIBuild,
  spCICommit,
  spCIMaster,
  spCIPullRequest,
  spMenuCanaryConfig,
  spMenuCanaryReport,
  spMenuClusters,
  spMenuConfig,
  spMenuLoadBalancers,
  spMenuPager,
  spMenuPipelines,
  spMenuProperties,
  spMenuSecurityGroups,
  spMenuTasks,
  spMenuTimeline,
  spel,
  templateFull,
  templateWorkflow,
} as const;

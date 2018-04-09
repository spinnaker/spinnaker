import { ScalingPolicyTypeRegistry } from '../ScalingPolicyTypeRegistry';

ScalingPolicyTypeRegistry.registerPolicyType({
  type: 'TargetTrackingScaling',
  summaryTemplateUrl: require('./targetTrackingSummary.html'),
});

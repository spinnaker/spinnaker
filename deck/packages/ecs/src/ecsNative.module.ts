import { CloudProviderRegistry, DeploymentStrategyRegistry, SETTINGS } from '@spinnaker/core';

// Register the shared ECS components before copying the provider configuration. The native
// identity reuses those component references for resource reads and details views.
import './ecs.module';
import ecsNativeLogo from './logo/ecs.logo.svg';

const ECS_NATIVE = 'ecs-native';

/**
 * Registers `ecs-native` as a Deck-visible identity backed by the existing `ecs` provider
 * components.
 *
 * Why it is registered at all: native deploy-stage clusters carry `cloudProvider: "ecs-native"`,
 * and core resolves several things by that key -- the deploy stage's
 * edit-cluster flow (CloneServerGroupModal and the `serverGroup.commandBuilder` delegate), the
 * provider logo/name in the deploy-stage cluster table, and deploy execution details.
 *
 * Accounts remain typed `ecs` (`accountType`), so account-type pickers keep offering `ecs` rather
 * than a second credential type that clouddriver doesn't have. Backend native resources are stamped
 * `cloudProvider: "ecs"` and use `isNative` for durable ownership, so aggregate ECS listings, the
 * card header override, and details views stay owned by `ecs`, preventing services from appearing
 * twice.
 *
 * The native registration reuses each `ecs` component reference; only the display name, logo and
 * account type differ. Deploy/clone behavior is selected by the stage's `cloudProvider` and is not
 * inferred from an account.
 */
export function registerEcsNativeProvider(): void {
  const ecsConfig = CloudProviderRegistry.getProvider('ecs');
  if (!ecsConfig) {
    return;
  }

  // CloudProviderRegistry only accepts providers present in SETTINGS.providers. ecs-native has no
  // settings of its own, so add a transient entry for the registration and remove it again: a
  // lasting duplicate of the ecs settings would make per-provider SETTINGS consumers (for example
  // the application config's provider defaults) treat ecs-native as a separately configured provider.
  const hasOwnSettings = !!SETTINGS.providers[ECS_NATIVE];
  if (!hasOwnSettings) {
    SETTINGS.providers[ECS_NATIVE] = { defaults: {} };
  }
  try {
    CloudProviderRegistry.registerProvider(ECS_NATIVE, {
      ...ecsConfig,
      name: 'EC2 Container Service (Native)',
      logo: { path: ecsNativeLogo },
      accountType: 'ecs',
    });
  } finally {
    if (!hasOwnSettings) {
      delete SETTINGS.providers[ECS_NATIVE];
    }
  }
}

registerEcsNativeProvider();

// The native ECS deployment strategy (rolling/blue-green, configured on the Native ECS Deployment
// wizard page) supersedes Spinnaker's own deployment strategies for this provider identity.
DeploymentStrategyRegistry.registerProvider(ECS_NATIVE, []);

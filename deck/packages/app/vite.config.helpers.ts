import { createRequire } from 'node:module';
import path from 'node:path';

type Environment = Record<string, string | undefined>;

const require = createRequire(import.meta.url);
const { resolveBuildOutput: resolveSharedBuildOutput } = require('./scripts/vite-output-path.js') as {
  resolveBuildOutput: (appRoot: string, env: Environment) => string;
};

export const IMPORT_META_ENV_KEYS = [
  'DYNAMIC_ROLLBACK_TIMEOUT_ENABLED',
  'MJ_PARENTPIPELINE_ENABLED',
  'MULTI_BLOCK_FAILURE_MESSAGES_ENABLED',
  'VITE_API_HOST',
  'VITE_API_VERSIONS_ENABLED',
  'VITE_ATLAS_WEB_COMPONENTS_URL',
  'VITE_AUTH_ENABLED',
  'VITE_AUTH_ENDPOINT',
  'VITE_BAKERY_DETAIL_URL',
  'VITE_CANARY_ACCOUNT',
  'VITE_CANARY_ENABLED',
  'VITE_CANARY_FEATURE_ENABLED',
  'VITE_CANARY_STAGES_ENABLED',
  'VITE_CHAOS_ENABLED',
  'VITE_CI_ENABLED',
  'VITE_DEBUG_ENABLED',
  'VITE_DISPLAY_TIMESTAMPS_IN_USER_LOCAL_TIME',
  'VITE_DRYRUN_ENABLED',
  'VITE_ENTITY_TAGS_ENABLED',
  'VITE_FIAT_ENABLED',
  'VITE_FUNCTIONS_ENABLED',
  'VITE_GCE_SCALE_DOWN_CONTROLS_ENABLED',
  'VITE_HTML5_ROUTING',
  'VITE_IAP_REFRESHER_ENABLED',
  'VITE_K8S_RAW_RESOURCES_ENABLED',
  'VITE_MANAGED_DELIVERY_ENABLED',
  'VITE_MANAGED_RESOURCES_ENABLED',
  'VITE_MANAGED_SERVICE_ACCOUNTS_ENABLED',
  'VITE_MD_GIT_INTEGRATION_ENABLED',
  'VITE_METRIC_STORE',
  'VITE_ON_DEMAND_CLUSTER_THRESHOLD',
  'VITE_REDUX_LOGGER',
  'VITE_TEMPLATES_ENABLED',
  'VITE_USE_CLASSIC_FIREWALL_LABELS',
];

export const PROCESS_ENV_KEYS = [
  'API_HOST',
  'API_VERSIONS_ENABLED',
  'ATLAS_WEB_COMPONENTS_URL',
  'AUTH_ENABLED',
  'AUTH_ENDPOINT',
  'BAKERY_DETAIL_URL',
  'CANARY_ACCOUNT',
  'CANARY_ENABLED',
  'CANARY_FEATURE_ENABLED',
  'CANARY_STAGES_ENABLED',
  'CHAOS_ENABLED',
  'CI_ENABLED',
  'DEBUG_ENABLED',
  'DISPLAY_TIMESTAMPS_IN_USER_LOCAL_TIME',
  'DRYRUN_ENABLED',
  'ENTITY_TAGS_ENABLED',
  'FIAT_ENABLED',
  'FUNCTIONS_ENABLED',
  'GCE_SCALE_DOWN_CONTROLS_ENABLED',
  'HTML5_ROUTING',
  'IAP_REFRESHER_ENABLED',
  'K8S_RAW_RESOURCES_ENABLED',
  'MANAGED_DELIVERY_ENABLED',
  'MANAGED_RESOURCES_ENABLED',
  'MANAGED_SERVICE_ACCOUNTS_ENABLED',
  'MD_GIT_INTEGRATION_ENABLED',
  'METRIC_STORE',
  'MULTI_BLOCK_FAILURE_MESSAGES_ENABLED',
  'ON_DEMAND_CLUSTER_THRESHOLD',
  'REDUX_LOGGER',
  'TEMPLATES_ENABLED',
  'TIMEZONE',
  'USE_CLASSIC_FIREWALL_LABELS',
];

export type TlsMode =
  | { kind: 'http' }
  | { kind: 'generated' }
  | { kind: 'custom'; cert: string; key: string; ca?: string };

export function buildPublicEnvDefines(env: Environment): Record<string, string> {
  const definitions: Record<string, string> = {};

  for (const key of IMPORT_META_ENV_KEYS) {
    definitions[`import.meta.env.${key}`] = env[key] === undefined ? 'undefined' : JSON.stringify(env[key]);
  }
  for (const key of PROCESS_ENV_KEYS) {
    definitions[`process.env.${key}`] = env[key] === undefined ? 'undefined' : JSON.stringify(env[key]);
  }

  return definitions;
}

export function resolveBuildOutput(appRoot: string, env: Environment): string {
  return resolveSharedBuildOutput(appRoot, env);
}

export function staticCopyTargets(appRoot: string, deckRoot: string, styleguideRoot: string, command: string) {
  return [
    {
      src: path.join(appRoot, 'public/plugin-manifest.json'),
      dest: '.',
      rename: { stripBase: true },
    },
    {
      src: path.join(deckRoot, 'version.json'),
      // The one-segment destination cancels v4's retained parent for a file above Vite's app root.
      dest: 'deck-root',
      rename: 'version.json',
    },
    {
      src: path.join(styleguideRoot, 'public/styleguide.html'),
      dest: '.',
      rename: { stripBase: true },
    },
    {
      src: path.join(appRoot, 'icons', command === 'build' ? 'prod-favicon.ico' : 'dev-favicon.ico'),
      dest: '.',
      rename: { stripBase: true, name: 'favicon.ico' },
    },
  ];
}

export function resolveSettingsPath(appRoot: string, env: Environment): string {
  return path.resolve(appRoot, env.SETTINGS_PATH || './src/settings.js');
}

export function getTlsMode(env: Environment): TlsMode {
  const cert = env.DECK_CERT;
  const key = env.DECK_KEY;

  if (Boolean(cert) !== Boolean(key)) {
    throw new Error('DECK_CERT and DECK_KEY must be provided together');
  }
  if (cert && key) {
    return { kind: 'custom', cert, key, ca: env.DECK_CA_CERT };
  }
  return env.DECK_HTTPS === 'true' ? { kind: 'generated' } : { kind: 'http' };
}

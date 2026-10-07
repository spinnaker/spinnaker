const path = require('node:path');

function resolveBuildOutput(appRoot, env) {
  const outputRoot = path.resolve(appRoot, 'dist');
  const environment = env.SPINNAKER_ENV;

  if (!environment) {
    return outputRoot;
  }

  const segments = environment.split(/[\\/]+/);
  const invalidEnvironment =
    path.isAbsolute(environment) ||
    path.win32.isAbsolute(environment) ||
    /^[A-Za-z]:/.test(environment) ||
    segments.includes('..');
  if (invalidEnvironment) {
    throw new Error(`Invalid SPINNAKER_ENV: ${environment}`);
  }

  const outputDir = path.resolve(outputRoot, ...segments);
  const relativeOutput = path.relative(outputRoot, outputDir);
  if (relativeOutput === '..' || relativeOutput.startsWith(`..${path.sep}`) || path.isAbsolute(relativeOutput)) {
    throw new Error(`Invalid SPINNAKER_ENV: ${environment}`);
  }

  return outputDir;
}

module.exports = { resolveBuildOutput };

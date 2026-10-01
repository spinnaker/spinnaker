function mergeViteEnvironment(loadEnv, mode, appRoot, processEnv = process.env) {
  return { ...loadEnv(mode, appRoot, ''), ...processEnv };
}

module.exports = { mergeViteEnvironment };

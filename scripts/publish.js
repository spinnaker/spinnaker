#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const prompts = require('prompts');
const kleur = require('kleur');
const ansi = require('sisteransi');
const yargs = require('yargs');
const { execSync } = require('child_process');

const MODULES_DIR = 'packages';

yargs.scriptName('publish.js').usage('$0 [args] [explicitPackage1...] [explicitPackage2...]').option('assert-master', {
  type: 'boolean',
  describe: 'turn off with --no-assert-master',
  default: true,
});

const explicitPackages = yargs.argv._;

const p = (str) => process.stdout.write(str);
const getPackagePath = (pkg) => path.resolve(`${MODULES_DIR}/${pkg}`);
const supportedPackages = new Set([
  'amazon',
  'appengine',
  'azure',
  'cloudfoundry',
  'core',
  'dcos',
  'docker',
  'ecs',
  'google',
  'huaweicloud',
  'kubernetes',
  'oracle',
  'tencentcloud',
  'titus',
]);

process.chdir(`${__dirname}/..`);
const files = fs.readdirSync(path.resolve(MODULES_DIR));
const pkgs = files.filter(
  (file) =>
    supportedPackages.has(file) &&
    fs.statSync(`${MODULES_DIR}/${file}`).isDirectory() &&
    fs.existsSync(`${getPackagePath(file)}/package.json`),
);

// Ensure 'gh' is installed
try {
  execSync('sh -c "which gh"');
} catch (error) {
  console.error(`The github command line app was not found.  Please install it using 'brew install github/gh/gh'`);
  console.error(`See: https://cli.github.com/manual/installation`);
  process.exit(1);
}

// Ensure user is using git:// protocol (not https) for upstream
const remotes = execSync('git remote -v')
  .toString()
  .split(/[\r\n]/);
const isHttpsUpstream = remotes.find((x) => x.includes('https://github.com/spinnaker/deck.git (push)'));
if (isHttpsUpstream) {
  const upstream = /^([\w]+)/.exec(isHttpsUpstream)[1];
  console.error(`The ${upstream} remote for spinnaker is using https protocol but should be using git`);
  console.error(`Run the following command to correct this:`);
  console.error();
  console.error(`git remote set-url ${upstream} git@github.com:spinnaker/deck.git`);
  process.exit(2);
}

const upstreamRemoteLine = remotes.find((x) => x.includes('git@github.com:spinnaker/deck.git (push)'));
const upstream = upstreamRemoteLine ? /^([\w]+)/.exec(upstreamRemoteLine)[1] : null;

// Ensure the working directory is clean and tracks master
try {
  if (yargs.argv['assert-master']) {
    execSync('sh -c "./scripts/assert_clean_master.sh"');
  }
} catch (error) {
  process.exit(3);
}

///////////////////////////////
// Fetch changelogs
///////////////////////////////

function status(message, index, total) {
  p(ansi.erase.lines(3));
  p(`${message}\n`);
  p(kleur.blue(`[${'='.repeat(index)}${' '.repeat(total - index)}]`) + ` (${index}/${total})\n`);
}

p('\n\n');
status(`Fetching changelogs`, 0, pkgs.length);
const changelogs = pkgs.map((pkg, index) => {
  status(`Fetching changelog for ${kleur.bold(pkg)}...`, index, pkgs.length);
  return {
    pkg,
    lines: execSync(`/bin/sh -c './scripts/show_changelog.sh "${getPackagePath(pkg)}/package.json"'`)
      .toString()
      .split(/[\r\n]/)
      .filter((str) => !!str),
  };
});

status(`Fetched ${pkgs.length} changelogs`, pkgs.length, pkgs.length);

///////////////////////////////
// Ask user to choose packages
///////////////////////////////

const choices = changelogs.map(({ pkg, lines }) => {
  const commitSummary = lines.length ? kleur.bold(`(${lines.length} unpublished commits)`) : kleur.dim('(not dirty)');
  const useExplicitSelections = explicitPackages.length > 0;
  return {
    value: pkg,
    selected: useExplicitSelections ? explicitPackages.includes(pkg) : lines.length > 0,
    title: `${lines.length ? kleur.green(pkg) : kleur.dim(pkg)} ${commitSummary}`,
  };
});

// Render a colorful changelog (above the menu) for the current item
function renderChangelog(changelog) {
  const lineCount = changelog.lines.length;
  const maxLineCount = changelogs.reduce((max, cl) => Math.max(max, cl.lines.length), 0);
  const maxWidth = typeof process.stdout.getWindowSize === 'function' ? process.stdout.getWindowSize()[0] : 100;

  const lines = changelog.lines
    .map((str) => str.replace(/^([a-f0-9]{7})[a-f0-9]{33} /, '$1 ')) // show first 7 chars of hash
    .map((str) => (str.length > maxWidth ? str.slice(0, maxWidth - 3) + kleur.dim('...') : str)) // truncate and ellipsis
    .map((str) => str.replace(/^[a-f0-9]{7} /, (match) => kleur.green(match)))
    .map((str) => str.replace(/(?:fix|chore|feat|docs|test|refactor)\([^)]*\): /, (match) => kleur.blue(match)))
    .map((str) => str.replace(/\(#[0-9]+\)$/, (match) => kleur.green(match)));

  // Add "blank" lines so the number of lines is consistent and the screen doesn't jump around
  return '\n' + (lines.length ? lines.join('\n') + '\n' : '') + '#\n'.repeat(maxLineCount - lineCount);
}

const prompt = {
  name: 'result',
  type: 'multiselect',
  message: 'Publish which packages?',
  optionsPerPage: 15,
  choices,
  onRender: function () {
    const changeLogForHighlighted = renderChangelog(changelogs[this.cursor]);
    const selections = this.value
      .filter((x) => x.selected)
      .map((x) => kleur.green(x.value))
      .join(', ');
    this.instructions = `${selections}\n${changeLogForHighlighted}`;
  },
};

(async () => {
  const { result } = await prompts(prompt);
  if (result) {
    bumpPackages(result);
  }
})();

///////////////////////////////
// Bump versions and create PR
///////////////////////////////

function bumpPackages(packages = []) {
  let branchNameCreated = null;
  const CHANGELOGTEMP = '___changelog.tmp.txt';
  try {
    const committer = execSync(`sh -c "git config --get user.name"`)
      .toString()
      .trim()
      .toLocaleLowerCase()
      .replace(/[^a-zA-Z]/g, '-');

    const publishes = [];
    // Update package.json and build the branch name
    packages.forEach((pkg) => {
      execSync(`sh -c "cd ${getPackagePath(pkg)}; npm version patch --no-git-tag-version"`);
      const version = JSON.parse(fs.readFileSync(`${getPackagePath(pkg)}/package.json`).toString()).version;
      const changelog = changelogs.find((cl) => cl.pkg === pkg);
      publishes.push({ pkg, version, lines: changelog && changelog.lines });
    });

    const branchString = publishes.map((p) => `${p.pkg}-${p.version}`).join('-');
    const branchName = `package-bump-${committer}-${branchString}`;
    execSync(`sh -c "git checkout -b ${branchName}"`);
    branchNameCreated = branchName;

    publishes.forEach(({ pkg, version, lines }) => {
      const commitMessage = `chore(${pkg}): publish ${pkg}@${version}\n\n\n${lines.join('\n')}`;
      const commitMessageFile = `____commitmessage.${pkg}.tmp`;
      try {
        fs.writeFileSync(commitMessageFile, commitMessage);
        execSync(`sh -c "git commit ${getPackagePath(pkg)}/package.json -F ${commitMessageFile}"`);
      } finally {
        fs.unlinkSync(commitMessageFile);
      }
    });

    // Push to upstream, if configured
    if (upstream) {
      try {
        execSync(`sh -c "git push ${upstream} ${branchName}"`);
      } catch (error) {
        // Probably failed because the user doesn't have write permission.
        // This is OK: 'gh pr create' will create the PR in the user's fork
      }
    }

    // export msg=$(cat msg) ; gh create pr -b "$msg"
    const changes = publishes.map((p) => `## ${p.pkg}@${p.version}\n\n${p.lines.join('\n')}`);
    fs.writeFileSync(CHANGELOGTEMP, changes.join('\n\n') + '\n\nPR created via `scripts/publish.js`\n\n');
    const title = 'chore(package): ' + publishes.map((p) => `${p.pkg}@${p.version}`).join(' ');
    execSync(`sh -c 'export msg=$(cat "${CHANGELOGTEMP}") ; gh pr create --title "${title}" --body "$msg"'`);
  } finally {
    execSync(`sh -c "git checkout master"`);
    branchNameCreated && execSync(`sh -c "git branch -D ${branchNameCreated}"`);
    fs.unlinkSync(CHANGELOGTEMP);
  }
}

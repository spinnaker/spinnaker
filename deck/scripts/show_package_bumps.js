#!/usr/bin/env node

process.chdir(`${__dirname}/../`);

const fs = require('fs');
const child = require('child_process');
const readline = require('readline');

const filename = process.argv[2];

if (!filename) {
  console.error('Usage: show_package_bumps.js <path-to-package-json>');
  process.exit(1);
}

if (!fs.existsSync(filename)) {
  console.error(`No package.json found at ${filename}`);
  process.exit(2);
}

const exec = child.exec(`git log -p -L '/"version":/':${filename}`);
exec.stderr.on('data', (line) => console.error(line));

const reader = readline.createInterface({ input: exec.stdout, console: false });

let lastSha;
reader.on('line', (line) => {
  const [_1, newSha] = /^commit (.*)$/.exec(line) || [];
  const [_2, version] = /^\+\s+"version"\s*:\s*"([^"]+)"/.exec(line) || [];
  lastSha = newSha || lastSha;
  if (version) {
    // eslint-disable-next-line no-console
    console.log(`${lastSha} ${version}`);
  }
});

#!/usr/bin/env python3

import contextlib
import subprocess
import os
import shutil
import tempfile

from .util import ReleaseBranch

@contextlib.contextmanager
def cd(newdir, cleanup):
    prevdir = os.getcwd()
    os.chdir(os.path.expanduser(newdir))
    try:
        yield
    finally:
        os.chdir(prevdir)
        cleanup()

@contextlib.contextmanager
def tempdir():
    dirpath = tempfile.mkdtemp()
    def cleanup():
        shutil.rmtree(dirpath)
    with cd(dirpath, cleanup):
        yield dirpath

def CherryPick(repo, commit, release, username, password):
    with tempdir() as dirpath:
        run([
            'git',
            'clone',
            'https://{}:{}@github.com/{}'.format(username, password, repo)
        ], dirpath, 
        desc='clone {}'.format(repo))

        dirpath = '{}/{}'.format(dirpath, repo.split('/')[-1])
        release_branch = ReleaseBranch(release)
        cherry_pick_branch = 'auto-cherry-pick-{}-{}'.format(release, commit)
        run(['git', 'checkout', release_branch], dirpath, 'checkout release branch {}'.format(release_branch))
        run(['git', 'config', 'user.name', username], dirpath, 'set user for repo')
        run(['git', 'config', 'user.email', '{}@spinnaker.io'.format(username)], dirpath, 'set email for repo')
        run(['git', 'cherry-pick', commit], dirpath, 'cherry pick commit {}'.format(commit))
        run(['git', 'checkout', '-b', cherry_pick_branch], dirpath, 'checkout {}'.format(cherry_pick_branch))
        run(['git', 'push', '-f', 'origin', cherry_pick_branch], dirpath, 'push {}'.format(cherry_pick_branch))
        return cherry_pick_branch

def run(args, cwd, desc=''):
    res = subprocess.run(
            args, 
            cwd=cwd, 
            stdout=subprocess.PIPE, 
            stderr=subprocess.STDOUT
        )

    if res.returncode != 0:
        raise RuntimeError('Command failed ({}) with exit code {}: \n\n```\n{}```'.format(desc,
            res.returncode,
            res.stdout.decode('utf-8')))

    return res

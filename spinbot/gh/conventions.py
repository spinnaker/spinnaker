import re

commit_types = ['feat', 'fix', 'docs', 'style', 'refactor', 'perf', 'test', 'chore', 'config']

branchre = re.compile('release-(\d+\.\d+\.x)')
commitre = re.compile('({})(.*): (.*)'.format('|'.join(commit_types)))

def ParseReleaseBranch(branch):
    branch = ReleaseBranchFor(branch)
    if branch is None:
        return None

    branch = branch.split('.')[:-1]
    branch = [ int(v) for v in branch ]
    return branch

def ReleaseBranchFor(branch):
    if not branch:
        return None
    m = branchre.match(branch)
    if m:
        return m.group(1)
    else:
        return None

def ParseCommitMessage(message):
    if not message:
        return None
    m = commitre.match(message)
    if m:
        return {
                'type': m.group(1),
                'scope': m.group(2),
                'subject': m.group(3)
        }
    else:
        return None

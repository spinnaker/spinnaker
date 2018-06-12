from .handler import Handler
from .pull_request_event import GetBaseBranch, GetPullRequest, GetTitle, GetRepo
from gh import ReleaseBranchFor, ParseCommitMessage

format_message = ('Features cannot be merged into release branches. The following commits ' +
    'are not tagged as one of "{}":\n\n{}\n\n' +
    'Read more about [commit conventions](https://www.spinnaker.io/community/contributing/submitting/#commit-message-conventions) ' +
    'and [patch releases](https://www.spinnaker.io/community/releases/release-cadence/#patching-the-release-candidate) here.')

class ReleaseBranchPullRequestHandler(Handler):
    def __init__(self):
        super().__init__()
        self.omit_repos = self.config.get('omit_repos', [])
        self.allowed_types = self.config.get(
            'allowed_types',
            ['fix', 'chore', 'docs', 'test']
        )

    def handles(self, event):
        return (event.type == 'PullRequestEvent'
            and event.payload.get('action') == 'opened'
            and ReleaseBranchFor(GetBaseBranch(event)) != None)

    def handle(self, g, event):
        repo = GetRepo(event)
        if repo in self.omit_repos:
            self.logging.info('Skipping {} because it\'s in omitted repo {}'.format(event, repo))
            return

        pull_request = GetPullRequest(g, event)
        if pull_request is None:
            self.logging.warn('Unable to determine PR that created {}'.format(event))
            return

        commits = pull_request.get_commits()
        bad_commits = []

        for commit in commits:
            message = ParseCommitMessage(commit.commit.message)
            if message is None or message.get('type') not in self.allowed_types:
                bad_commits.append(commit.commit)

        if len(bad_commits) > 0:
            pull_request.create_issue_comment(format_message.format(
                ', '.join(self.allowed_types),
                '\n\n'.join(map(lambda c: '{}: {}'.format(c.sha, c.message), bad_commits))
            ))

ReleaseBranchPullRequestHandler()

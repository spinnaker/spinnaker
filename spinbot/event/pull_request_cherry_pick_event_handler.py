from gh import AddLabel, RemoveLabel, ParseCommitMessage
from .handler import Handler
from .command import GetCommands
from .pull_request_event import GetPullRequest, GetRepo

invalid_command_format = ("You must specify exactly 1 release to cherry-pick " +
        "this commit into. For example:\n\n" +
        "> @spinnakerbot cherry-pick 1.10")

not_merged = "Only merged PRs can be cherry picked into a release branch"

class PullRequestCherryPickEventHandler(Handler):
    def __init__(self):
        super().__init__()
        self.omit_repos = self.config.get('omit_repos', [])

    def handles(self, event):
        return (event.type == 'IssueCommentEvent'
            and (event.payload.get('action') == 'created'
                or event.payload.get('action') == 'edited'))

    def handle(self, g, event):
        # avoid fetching until needed
        pull_request = None
        commit = None
        repo = GetRepo(event)
        if repo in self.omit_repos:
            self.logging.info('Skipping {} because it\'s in omitted repo {}'.format(event, repo))
            return

        for command in GetCommands(event.payload.get('comment', {}).get('body')):
            if command[0] == 'cherry-pick':
                if len(command) != 2:
                    pull_request.create_issue_comment(invalid_command_format)
                    return
                release = command[1]
                if pull_request is None:
                    pull_request = GetPullRequest(g, event)
                    commit = pull_request.merge_commit_sha
                self.do_cherry_pick(g, pull_request, repo, commit, release)

    def do_cherry_pick(self, g, pull_request, repo, commit, release):
        if not pull_request.is_merged:
            pull_request.create_issue_comment(not_merged)

        try:
            p = g.cherry_pick(repo=repo, release=release, commit=commit)
            pull_request.create_issue_comment("Cherry pick successful: #{}".format(p.number))
        except RuntimeError as e:
            pull_request.create_issue_comment("Cherry pick failed: {}".format(str(e)))


PullRequestCherryPickEventHandler()

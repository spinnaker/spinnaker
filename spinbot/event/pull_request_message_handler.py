from .handler import Handler
from .pull_request_event import GetBaseBranch, GetPullRequest, GetTitle, GetRepo
from gh import ReleaseBranchFor, ParseCommitMessage

bad_contents = (['### Instructions (that you should delete before submitting):', 
    'We prefer small, well tested pull requests.'])
message = ('Please delete the pull request instructions from the body of your pull request message.\n\n' 
        + 'The instructions start with the line:\n\n> {}\n\n'
        + 'You can reopen your pull request when this has been addressed.')

class PullRequestMessageHandler(Handler):
    def __init__(self):
        super().__init__()
        self.omit_repos = self.config.get('omit_repos', [])

    def handles(self, event):
        return (event.type == 'PullRequestEvent'
            and event.payload.get('action') == 'opened')

    def handle(self, g, event):
        repo = GetRepo(event)
        if repo in self.omit_repos:
            self.logging.info('Skipping {} because it\'s in omitted repo {}'.format(event, repo))
            return

        pull_request = GetPullRequest(g, event)
        if pull_request is None:
            self.logging.warn('Unable to determine PR that created {}'.format(event))
            return
        
        if pull_request.body is None:
            return

        for bad_message in bad_contents:
            if bad_message in pull_request.body:
                pull_request.create_issue_comment(message.format(bad_message))
                pull_request.edit(state='closed')
                break

PullRequestMessageHandler()

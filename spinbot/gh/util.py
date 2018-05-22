import github

def IssueRepo(issue):
    return '/'.join(issue.url.split('/')[-4:-2])

def HasLabel(issue, name):
    label = next((l for l in issue.get_labels() if l.name == name), None)
    return label is not None

def AddLabel(gh, issue, name, create=True):
    if HasLabel(issue, name):
        return

    label = gh.get_label(IssueRepo(issue), name, create=create) 
    if label is None:
        issue.create_comment(
            'Sorry! "{}" is not a label yet, and I don\'t create '.format(name)
            + 'labels to avoid spam.'
        )
        return
    issue.add_to_labels(label)

def ObjectType(o):
    if isinstance(o, github.Issue.Issue):
        return 'issue'
    elif isinstance(o, github.Repository.Repository):
        return 'repository'
    else:
        return None

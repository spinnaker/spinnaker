def GetIssue(g, event):
    url = event.payload.get('issue', {}).get('url')
    if not url:
        return None

    repo = '/'.join(url.split('/')[-4:-2])
    number = int(url.split('/')[-1])

    if repo == None or number == None:
        return None

    return g.get_issue(repo, number)

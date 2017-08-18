The owner email for the following applications matches multiple users:

<#foreach event in notification.additionalContext.events>${event.email} owns...
<#foreach application in event.applications>- ${application.name}
</#foreach>

</#foreach>
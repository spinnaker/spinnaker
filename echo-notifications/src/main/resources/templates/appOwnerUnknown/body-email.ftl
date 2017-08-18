The following email addresses are not recognized employee or group addresses but own applications.

<#foreach event in notification.additionalContext.events>${event.email} owns...
<#foreach application in event.applications>- ${application.name}
</#foreach>

</#foreach>
The following users are ex-employees but still own applications. We could not identify managers or team members to reassign to.

<#foreach event in notification.additionalContext.events>${event.user.name}<${event.user.email}> owns...
<#foreach application in event.applications>- ${application.name}
</#foreach>

</#foreach>
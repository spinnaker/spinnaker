<p>The following users are ex-employees but still own applications. We have identified possible managers or team members to reassign to.</p>
<#foreach event in notification.additionalContext.events>
${event.user.name} (${event.user.email}) owns...
<ul>
  <#foreach application in event.applications><li>${application.name}</li></#foreach>
</ul>
<p>Suggest reassigning to ${event.suggestedUser.name} (${event.suggestedUser.email})</p>
<br/>
</#foreach>

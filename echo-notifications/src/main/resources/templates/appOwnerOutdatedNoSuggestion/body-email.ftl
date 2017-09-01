<p>The following users are ex-employees but still own applications. We could not identify managers or team members to reassign to.</p>
<#foreach event in notification.additionalContext.events>
${event.user.name} (${event.user.email}) owns...
<ul>
  <#foreach application in event.applications><li>${application.name}</li></#foreach>
</ul>
<br/>
</#foreach>

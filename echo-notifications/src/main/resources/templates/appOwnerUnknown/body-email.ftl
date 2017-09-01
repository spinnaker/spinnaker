<p>The following email addresses are not recognized employee or group addresses but own applications.</p>
<#foreach event in notification.additionalContext.events>
${event.email} owns...
<ul>
  <#foreach application in event.applications><li>${application.name}</li></#foreach>
</ul>
<br/>
</#foreach>

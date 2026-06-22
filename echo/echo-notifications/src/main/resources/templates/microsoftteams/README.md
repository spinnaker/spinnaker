# Microsoft Teams Notification Templates

This directory contains Jinja2 templates for Microsoft Teams notifications in Spinnaker Echo.

## ⚠️ Important: Office 365 Connectors Retirement

**Microsoft is retiring Office 365 Connectors (deadline: March 31, 2026)**

If you're using legacy Incoming Webhooks (Office 365 Connectors), you must migrate to the new **Power Automate Workflows** webhooks. 

### Migration Steps

1. **Create a new webhook in Power Automate**:
   - In Microsoft Teams, go to the channel where you want notifications
   - Click the "..." menu → Workflows → "Post to a channel when a webhook request is received"
   - Configure the workflow and copy the new webhook URL
   - The new URL format looks like: `https://prod-XX.eastus.logic.azure.com:443/workflows/.../triggers/manual/paths/invoke?...`

2. **Update your Spinnaker configuration**:
   - Replace old webhook URLs with the new Power Automate webhook URLs
   - No code changes needed - the templates work with both formats

3. **Test your notifications**:
   - Trigger a test notification to verify the new webhook works
   - Adaptive Cards (default) provide the best experience

**References:**
- [Official Microsoft Announcement](https://devblogs.microsoft.com/microsoft365dev/retirement-of-office-365-connectors-within-microsoft-teams/)
- [Microsoft Support: Send messages using incoming webhooks](https://support.microsoft.com/en-us/office/send-messages-in-teams-using-incoming-webhooks-323660ec-12ca-40b1-a1d3-a3df47e808c4)

## Overview

Microsoft Teams notifications use Jinja2 templates to generate the JSON payload sent to Teams webhooks. This allows for flexible customization of notification messages while maintaining compatibility with Microsoft Teams API structure.

**Default Format:** The default templates use [Adaptive Cards](https://docs.microsoft.com/en-us/microsoftteams/platform/task-modules-and-cards/cards/cards-reference#adaptive-card), which is the current recommended format for Microsoft Teams. Adaptive Cards provide rich, interactive content and are supported across Microsoft Teams, Outlook, and other Microsoft 365 applications.

## Available Templates

### event-notification.jinja
Used by `MicrosoftTeamsNotificationService` for event-based notifications. These are typically triggered by notification stages in pipelines.

**Available Variables:**
- `correlationId` - Unique identifier for the message
- `summary` - Brief summary of the notification
- `message` - Detailed message content
- `executionUrl` - Link to the execution in Spinnaker
- `themeColor` - Color for the message card (hex string without #)
- `spinnakerUrl` - Base URL of Spinnaker
- `notification` - Full notification object
- `interactiveActions` - (Optional) Interactive action configuration

### pipeline-notification.jinja
Used by `MicrosoftTeamsNotificationAgent` for pipeline and stage notifications. These are configured via application notification preferences.

**Available Variables:**
- `correlationId` - Unique identifier for the message
- `summary` - Brief summary of the notification
- `cardTitle` - Title for the message card
- `themeColor` - Color for the message card (hex string without #)
- `applicationName` - Name of the Spinnaker application
- `executionName` - Name of the pipeline execution
- `executionUrl` - Link to the execution in Spinnaker
- `eventName` - Name of the stage or pipeline
- `eventNameLabel` - Label for eventName (e.g., "Stage Name", "Pipeline Name")
- `description` - Execution description (if provided)
- `customMessage` - Custom user-provided message (if configured)
- `status` - Execution status (capitalized)
- `spinnakerUrl` - Base URL of Spinnaker
- `event` - Full event object
- `configType` - Type of notification ("pipeline", "stage", or "task")

## Theme Colors

Default theme colors used based on status:
- **Green** (`73DB69`) - Completed successfully
- **Red** (`EB1A1A`) - Failed
- **Blue** (`0076D7`) - In progress or other states

## Custom Templates

To use custom templates, configure the `template-path` property in your Echo configuration:

```yaml
microsoftteams:
  enabled: true
  template-path: /path/to/custom/templates
```

Place your custom templates in the specified directory with the same filenames:
- `event-notification.jinja`
- `pipeline-notification.jinja`

If a custom template is not found or fails to load, Echo will automatically fall back to the default bundled templates.

## Template Structure

Templates must generate valid JSON for Microsoft Teams webhooks. The default templates use the Adaptive Card format, which provides better styling and interactivity.

### Adaptive Card Example (Default Format)

```jinja
{
  "type": "message",
  "attachments": [
    {
      "contentType": "application/vnd.microsoft.card.adaptive",
      "content": {
        "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
        "type": "AdaptiveCard",
        "version": "1.4",
        "body": [
          {
            "type": "Container",
            "style": "{% if themeColor == 'EB1A1A' %}attention{% elif themeColor == '73DB69' %}good{% else %}emphasis{% endif %}",
            "items": [
              {
                "type": "TextBlock",
                "text": "{{ cardTitle }}",
                "weight": "Bolder",
                "size": "Large",
                "wrap": true
              }
            ]
          },
          {
            "type": "FactSet",
            "facts": [
              {% if applicationName %}
              {
                "title": "Application",
                "value": "{{ applicationName }}"
              }{% if status %},{% endif %}
              {% endif %}
              {% if status %}
              {
                "title": "Status",
                "value": "{{ status }}"
              }
              {% endif %}
            ]
          }
        ],
        "actions": [
          {
            "type": "Action.OpenUrl",
            "title": "View Details",
            "url": "{{ executionUrl }}"
          }
        ]
      }
    }
  ]
}
```

### MessageCard Format (Legacy - Limited Support)

**⚠️ Limitations with MessageCard in Power Automate Workflows:**
- Interactive cards (HttpPOST actions) are **not supported**
- Custom bot icon and name are **not available**
- Messages display with the default Workflows bot (Flow bot) identity
- **Recommendation**: Use Adaptive Cards for new implementations

If you absolutely need MessageCard format for legacy compatibility, create a custom template:

```jinja
{
  "@context": "http://schema.org/extensions",
  "@type": "MessageCard",
  "summary": "{{ summary }}",
  "themeColor": "{{ themeColor }}",
  "sections": [
    {
      "activityTitle": "{{ cardTitle }}",
      "facts": [
        {% if applicationName %}
        {
          "name": "Application",
          "value": "{{ applicationName }}"
        }
        {% endif %}
      ]
    }
  ]
}
```

## Jinja2 Syntax

Templates use Jinja2 syntax:
- `{{ variable }}` - Variable interpolation
- `{% if condition %}...{% endif %}` - Conditional blocks
- `{% for item in list %}...{% endfor %}` - Loops
- `{% if not loop.last %},{% endif %}` - Add commas between list items

## Troubleshooting

If template rendering fails:
1. Check Echo logs for detailed error messages
2. Verify your template generates valid JSON
3. Ensure all required variables are used correctly
4. Test your template with sample data
5. Echo will fall back to default templates if custom templates fail

## Microsoft Teams API Changes

**Important:** Microsoft Teams has updated their webhook API structure. The default templates now use [Adaptive Cards](https://docs.microsoft.com/en-us/microsoftteams/platform/task-modules-and-cards/cards/cards-reference#adaptive-card), which is the recommended format for all new implementations.

### Benefits of Adaptive Cards

- **Modern Design**: Better styling with Container styles (attention, good, emphasis)
- **Cross-Platform**: Works in Teams, Outlook, and other Microsoft 365 apps
- **Responsive**: Automatically adapts to different screen sizes
- **Rich Content**: Supports images, videos, and complex layouts
- **Future-Proof**: Active development and new features from Microsoft

If you need to maintain compatibility with older systems, you can create custom templates using the legacy MessageCard format (see example above).

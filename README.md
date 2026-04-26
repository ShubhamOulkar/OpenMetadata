# Slack App Demo

## Changes Included in Current Demo Setup
This demo introduces a native Slack integration allowing users to search and discover OpenMetadata assets directly from Slack using slash commands.
* **Slash Command Endpoint (`/api/v1/slack/command`)**: Parses `/metadata search <type> <query>` and queries the OpenMetadata Elasticsearch/OpenSearch backend.
* **Interactive UI Blocks**: Returns results using Slack's rich Block Kit UI, complete with interactive "View" buttons that link directly to the assets in the OpenMetadata UI.
* **Interactivity Endpoint (`/api/v1/slack/interactive`)**: Silently acknowledges button clicks to prevent Slack from displaying interactive timeout errors.
* **Smart Search Parsing**: Automatically isolates searches to specific entity types (e.g., `table`, `topic`, `dashboard`) if specified, falling back to a global search restricted to asset names.

## How to Setup Slack App for a Project
1. **Create a Slack App**: Go to [api.slack.com/apps](https://api.slack.com/apps) and create a new app.
2. **Enable Slash Commands**: Create a new command called `/metadata` and point the Request URL to your OpenMetadata backend: `https://<your-om-domain>/api/v1/slack/command`.
3. **Enable Interactivity**: Go to "Interactivity & Shortcuts", toggle it on, and set the Request URL to: `https://<your-om-domain>/api/v1/slack/interactive`.
4. **Install App**: Install the app into your Slack workspace.

> **Note:** User authorization and Slack request signature verification (HMAC) are not implemented for this setup. The endpoints are currently open to demonstrate the functionality.

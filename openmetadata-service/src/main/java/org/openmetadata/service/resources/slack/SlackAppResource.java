package org.openmetadata.service.resources.slack;

import com.slack.api.model.block.LayoutBlock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.service.apps.slack.SlackSearchHandler;

/**
 * JAX-RS resource that handles incoming Slack slash commands.
 * Registered manually in OpenMetadataApplication.
 * Endpoint: POST /api/v1/slack/command
 */
@Slf4j
@Path("/v1/slack")
@Tag(name = "Slack", description = "Slack App integration endpoints.")
@Produces(MediaType.APPLICATION_JSON)
public class SlackAppResource {

  private final SlackSearchHandler searchHandler;

  public SlackAppResource() {
    this.searchHandler = new SlackSearchHandler();
  }

  @POST
  @Path("/command")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Operation(
      operationId = "slackSlashCommand",
      summary = "Handle Slack slash commands",
      description = "Receives and processes Slack slash commands such as /metadata search.")
  public Response handleCommand(
      @FormParam("command") String command,
      @FormParam("text") String text,
      @FormParam("user_id") String userId,
      @FormParam("channel_id") String channelId,
      @FormParam("response_url") String responseUrl) {

    LOG.info(
        "Slack command received: command={}, text={}, userId={}, channelId={}",
        command, text, userId, channelId);

    if (text != null && text.toLowerCase().startsWith("search")) {
      String query = text.substring("search".length()).trim();
      if (query.isEmpty()) {
        String json = com.slack.api.util.json.GsonFactory.createSnakeCase().toJson(
            new SlackTextResponse("Please provide a search term. Usage: `/metadata search <term>`"));
        return Response.ok(json).build();
      }
      List<LayoutBlock> blocks = searchHandler.search(query);
      String json = com.slack.api.util.json.GsonFactory.createSnakeCase().toJson(new SlackBlockResponse(blocks));
      return Response.ok(json).build();
    }

    // Default help message
    String json = com.slack.api.util.json.GsonFactory.createSnakeCase().toJson(
        new SlackTextResponse(
            String.format(
                "Hello <@%s>! Here's what I can do:\n• `/metadata search <term>` — Search all assets by name\n• `/metadata search table <term>` — Search only tables\n• Supported types: `table`, `topic`, `dashboard`, `pipeline`, `mlmodel`, `glossary`",
                userId)));
    return Response.ok(json).build();
  }

  @POST
  @Path("/interactive")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Operation(
      operationId = "slackInteractive",
      summary = "Handle Slack interactivity",
      description = "Silently acknowledges Slack interactive component actions (like button clicks).")
  public Response handleInteractive(@FormParam("payload") String payload) {
    // Slack sends a 'payload' JSON string when a user clicks a button.
    // Since our 'View' buttons use a URL to open the browser, we don't need to process the payload.
    // We just need to return an empty 200 OK so Slack knows we received it and doesn't show an error.
    LOG.info("Slack interactive payload received");
    return Response.ok().build();
  }

  /** Simple text-only Slack response. */
  public static class SlackTextResponse {
    @Getter @Setter private String responseType = "ephemeral";
    @Getter @Setter private String text;

    public SlackTextResponse() {}

    public SlackTextResponse(String text) {
      this.text = text;
    }
  }

  /** Block Kit response for rich Slack messages. */
  public static class SlackBlockResponse {
    @Getter @Setter private String responseType = "ephemeral";
    @Getter @Setter private String text = "Search results";
    @Getter @Setter private List<LayoutBlock> blocks;

    public SlackBlockResponse() {}

    public SlackBlockResponse(List<LayoutBlock> blocks) {
      this.blocks = blocks;
    }
  }
}

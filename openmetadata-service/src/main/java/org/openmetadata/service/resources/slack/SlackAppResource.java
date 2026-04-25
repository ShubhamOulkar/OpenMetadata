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
        return Response.ok(new SlackTextResponse("Please provide a search term. Usage: `/metadata search <term>`")).build();
      }
      List<LayoutBlock> blocks = searchHandler.search(query);
      return Response.ok(new SlackBlockResponse(blocks)).build();
    }

    // Default help message
    return Response.ok(
        new SlackTextResponse(
            String.format(
                "Hello <@%s>! Here's what I can do:\n• `/metadata search <term>` — Search for data assets",
                userId)))
        .build();
  }

  /** Simple text-only Slack response. */
  public static class SlackTextResponse {
    @Getter @Setter private String text;

    public SlackTextResponse() {}

    public SlackTextResponse(String text) {
      this.text = text;
    }
  }

  /** Block Kit response for rich Slack messages. */
  public static class SlackBlockResponse {
    @Getter @Setter private List<LayoutBlock> blocks;

    public SlackBlockResponse() {}

    public SlackBlockResponse(List<LayoutBlock> blocks) {
      this.blocks = blocks;
    }
  }
}

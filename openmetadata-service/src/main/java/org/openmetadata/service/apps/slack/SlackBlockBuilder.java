package org.openmetadata.service.apps.slack;

import com.slack.api.model.block.DividerBlock;
import com.slack.api.model.block.HeaderBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.ButtonElement;

/** Utility class for building Slack Block Kit layout blocks. */
public final class SlackBlockBuilder {

  private SlackBlockBuilder() {}

  public static HeaderBlock header(String text) {
    return HeaderBlock.builder()
        .text(PlainTextObject.builder().text(text).emoji(true).build())
        .build();
  }

  public static SectionBlock section(String markdownText) {
    return SectionBlock.builder()
        .text(MarkdownTextObject.builder().text(markdownText).build())
        .build();
  }

  public static SectionBlock sectionWithLinkButton(
      String markdownText, String buttonLabel, String url, String actionId) {
    return SectionBlock.builder()
        .text(MarkdownTextObject.builder().text(markdownText).build())
        .accessory(
            ButtonElement.builder()
                .text(PlainTextObject.builder().text(buttonLabel).emoji(true).build())
                .url(url)
                .actionId(actionId)
                .build())
        .build();
  }

  public static DividerBlock divider() {
    return DividerBlock.builder().build();
  }

  /** Truncates a string to maxLen characters, appending "..." if needed. */
  public static String truncate(String s, int maxLen) {
    if (s == null) return "";
    return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
  }
}

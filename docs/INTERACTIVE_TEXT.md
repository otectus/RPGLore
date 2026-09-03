# Interactive Text in Lore Books

Pack authors can add interactive elements to lore book pages using vanilla JSON text components: hover tooltips, clickable links, and page navigation.

## Page Format

Each page is a vanilla Minecraft JSON text component. A simple string page:

```json
"pages": ["Just plain text."]
```

Or a component object with formatting and interaction:

```json
"pages": [{
  "text": "",
  "extra": [
    { "text": "Click me", "clickEvent": { "action": "open_url", "value": "https://..." } }
  ]
}]
```

RPG Lore adds no custom markup — all formatting is vanilla component syntax.

## Hover Text

Use a `hoverEvent` with action `show_text` to display a tooltip on hover:

```json
{
  "text": "Hover me",
  "hoverEvent": {
    "action": "show_text",
    "contents": { "text": "Tooltip text", "italic": true }
  }
}
```

## Click Events

A `clickEvent` triggers an action when clicked. RPG Lore handles these actions:

**suggest_command** — Inserts a command into chat without executing. Vanilla behavior.

**copy_to_clipboard** — Copies the value to the player's clipboard. Vanilla behavior.

**open_url** — Opens a URL in the browser with a vanilla confirmation screen. Vanilla behavior.

**change_page** — Jumps to a content page number. Values are **1-based and do not count the title page**. Content page 1 is the first page after the title. Non-numeric values have no effect; 0 or negative numbers clamp to the title page; numbers beyond the book's last page clamp to the last content page.

```json
{ "text": "Go to page 3", "clickEvent": { "action": "change_page", "value": "3" } }
```

**run_command** — Executes a command as the player. **Blocked by default**. Requires the server config setting `reader.allowRunCommandClicks=true` to enable. It defaults to `false` because lore packs are third-party content and should not silently execute commands without operator consent.

**open_file** — Opening local files is never permitted and always ignored for security.

## Translate Components

Use vanilla `translate` components with translation keys provided by a resource pack:

```json
{ "translate": "my.custom.key", "with": [ "value1" ] }
```

This uses vanilla Minecraft translation — RPG Lore adds no additional features.

## Title Page Wrapping

The auto-generated title page fits titles to two lines maximum by scaling:

1. Title is rendered at scales from 1.0× down to 0.75× (in 5% steps).
2. The largest scale fitting two lines is chosen.
3. If the title is still too long at 0.75×, the last line is truncated and an ellipsis (`...`) appended.

This prevents shrinking into illegibility or overlapping the ornament and author line.

## Reading and Codex

Opening a physical lore book marks it as read in the Lore Codex (when Codex tracking is enabled). Additionally, clicking "Open" on a Codex entry marks it read immediately. Read state is persisted per player and includes a discovery timestamp.

## Testing Your Book

1. **Use the example.** See `docs/examples/interactive_text_example.json` for a working reference showing hover text, all click event types, and title wrapping.

2. **Validate.** Use `/rpglore validate <book_id>` (OP level 2) to check JSON syntax and click event targets without reloading. Example: `/rpglore validate rpg_lore:interactive_text_example`

3. **Test in-game.** Place your book JSON in `config/rpg_lore/books/`, reload with `/rpglore reload`, then test with `/rpglore give @s <book_id>`. All interactive features work on content pages; the title page does not support clicks.

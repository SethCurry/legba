import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

export default function (pi: ExtensionAPI) {
  pi.on("tool_execution_end", async (event, ctx) => {
    if (event.toolName === "write" || event.toolName === "edit") {
      // TODO fix parents
    }
  });
}

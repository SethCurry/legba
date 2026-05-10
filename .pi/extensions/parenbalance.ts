import {
  ExtensionAPI,
  isToolCallEventType,
} from "@earendil-works/pi-coding-agent";
import child_process from "child_process";

export default function (pi: ExtensionAPI) {
  pi.on("tool_result", async (event, ctx) => {
    if (
      isToolCallEventType("write", event) ||
      isToolCallEventType("edit", event)
    ) {
      ctx.ui.notify(JSON.stringify(event));
      if (event.input.path.endsWith(".clj")) {
        child_process.exec("brepl balance " + event.input.path);
        ctx.ui.notify("Balanced parens in " + event.input.path);
      }
    }
  });
}

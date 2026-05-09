import {
  ExtensionAPI,
  isToolCallEventType,
} from "@earendil-works/pi-coding-agent";
import child_process from "child_process";

export default function (pi: ExtensionAPI) {
  pi.on("tool_execution_end", async (event, ctx) => {
    if (
      isToolCallEventType("write", event) ||
      isToolCallEventType("edit", event)
    ) {
      child_process.exec("brepl balance " + event.input.path);
    }
  });
}

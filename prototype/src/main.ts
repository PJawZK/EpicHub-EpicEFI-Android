import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/shell.css";

import { createApp } from "./app/create-app";
import { createPrototypeDependencies } from "./app/dependencies";

const root = document.querySelector<HTMLElement>("#app");
if (!root) {
  throw new Error("EpicHub prototype mount point #app was not found");
}

void createApp(root, createPrototypeDependencies());

# 🚀 Project Tabs for Linux (PhpStorm/IntelliJ)

A professional extension for the IntelliJ Platform that brings macOS-style project tab management to Linux environments.

# 📖 Overview

On macOS, JetBrains IDEs offer a native feature to merge all open project windows into a single window with tabs. On Linux, this functionality is natively unavailable, forcing developers to manage multiple independent system windows and switch between them using Alt+Tab.

Project Tabs solves this by injecting a global navigation bar directly into the IDE. It allows you to switch between open projects instantly while maintaining complete process isolation for each workspace.
# ✨ Key Features

  - Project Tab Bar: A dynamic navigation bar injected at the top of the IDE (above the file editor).

  - True Context Isolation: Unlike the "Attach Project" feature, each project keeps its own Index, Terminal, Version Control, and Tool Windows.

  - Instant Switching: Toggle between microservices or frontend/backend projects with a single click.

  - Real-Time Sync: The tab list automatically updates across all open windows whenever a project is opened or closed.

  - Native Integration: Full support for IntelliJ themes (Light, Darcula, and custom themes) for a seamless visual experience.

  - Linux Focus Optimization: Tailored for GNOME and KDE to ensure window focusing is smooth and immediate.

# 🛠 Technical Architecture

The plugin leverages core IntelliJ Platform APIs for maximum stability:

  - UI Anchor: IdeRootPaneNorthExtension for a fixed, non-intrusive UI placement.

  - Lifecycle Tracking: ProjectManagerListener to monitor project sessions.

  - Inter-Window Communication: MessageBus to synchronize the UI state across multiple IDE instances.

# 📄 License

Distributed under the MIT License. See the LICENSE file for more details.

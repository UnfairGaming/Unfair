# Unfair

Unfair is a Forge client mod for Minecraft 1.8.9. It is based on OpenMyau+7 and provides more customizable modules.

### Credits

- OpenMyau https://github.com/60124808866/OpenMyau
- Epilogue https://github.com/qm123pz/Epilogue-Client
- And others...

## Usage

### Opening the ClickGUI

Press **Right Shift** by default to open the ClickGUI. You can also toggle the `ClickGui` module in-game with:

```text
.toggle ClickGui
```

### Commands

Commands must start with `.` and should be entered in the Minecraft chat box:

| Command | Description |
| --- | --- |
| `.help` / `.commands` | View the command list |
| `.toggle <module>` / `.t <module>` | Toggle a module |
| `.bind <module> <key>` / `.b` | Set a module keybind |
| `.bind <module> none` | Clear a module keybind |
| `.bind list` | View existing keybinds |
| `.config load <name>` | Load a configuration |
| `.config save <name>` | Save a configuration |
| `.config list` | View the configuration list |
| `.config folder` | Open the configuration folder |
| `.friend ...` | Manage friends |
| `.target ...` | Manage targets |
| `.module ...` | View or manage modules |
| `.list` | View the module list |
| `.show` / `.hide` | Show or hide modules |
| `.playerlist` / `.players` | View the current player list |
| `.itemname` / `.item` | View information about the held item |
| `.vclip ...` | Perform a vertical clip |
| `.username` / `.name` / `.ign` | Copy the current username |
| `.denick <name>` | Look up a player's real name and UUID |

For detailed command parameters, use:

```text
.help
```

### Configuration Files

Configuration files are stored in:

```text
config/Unfair/
```

The default configuration file is `default.json`. Module states, keybinds, hidden states, and module properties are stored in JSON. Changes are saved automatically, and the current configuration is also saved when the game exits.

## Development Environment

- Minecraft `1.8.9`
- Minecraft Forge `11.15.1.2318`
- Java `8`
- Gradle Wrapper `8.8`
- Architectury Loom / ForgeGradle compatible development environment
- SpongePowered Mixin

The project is written using the Java 8 language level. JDK 8 is recommended for development, while JDK 17 or 21 can be used for building. If you use a newer JDK, make sure that Gradle, Loom, and the legacy Minecraft dependencies work correctly.

## Building

Windows:

```powershell
.\gradlew.bat build
```

After the build completes, the distributable JAR is located at:

```text
build/libs/Unfair-<version>.jar
```

The project also provides a Forge client run configuration. Start the development client with:

```powershell
.\gradlew.bat runClient
```

The first build downloads Minecraft, Forge, mappings, and other Gradle dependencies, so an internet connection is required.

## Project Structure

```

```

When the mod starts, it scans the category packages and automatically registers `Module` implementations. Therefore, adding a regular module usually only requires placing the class in the appropriate category package and extending `Module`.

## License

This project is released under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.

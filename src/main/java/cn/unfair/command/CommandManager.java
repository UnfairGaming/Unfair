package cn.unfair.command;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.PacketEvent;
import cn.unfair.module.Module;
import cn.unfair.property.Property;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.client.C01PacketChatMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CommandManager {
    public ArrayList<Command> commands;

    public CommandManager() {
        this.commands = new ArrayList<>();
    }

    public void handleCommand(String string) {
        List<String> params = Arrays.asList(string.substring(1).trim().split("\\s+"));
        ArrayList<String> arrayList = new ArrayList<>(params);
        if (params.get(0).isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sUnknown command&r", Unfair.clientName).replace("&", "§"));
        } else {
            for (Command command : Unfair.commandManager.commands) {
                for (String name : command.names) {
                    if (params.get(0).equalsIgnoreCase(name)) {
                        command.runCommand(arrayList);
                        return;
                    }
                }
            }
            ChatUtil.sendFormatted(String.format("%sUnknown command (&o%s&r)&r", Unfair.clientName, params.get(0)).replace("&", "§"));
        }
    }

    public boolean isTypingCommand(String string) {
        if (string == null || string.length() < 2) {
            return false;
        } else {
            return string.charAt(0) == '.' && Character.isLetterOrDigit(string.charAt(1));
        }
    }

    public int getAutocompleteStart(String text, int cursor) {
        int space = text.lastIndexOf(' ', Math.max(0, cursor - 1));
        if (space < 0 && text != null && text.startsWith(".")) {
            return 1;
        }
        return space < 0 ? 0 : space + 1;
    }

    public List<String> getAutocompleteSuggestions(String text, int cursor) {
        ArrayList<String> suggestions = new ArrayList<>();
        if (text == null || !text.startsWith(".")) {
            return suggestions;
        }

        cursor = Math.max(0, Math.min(cursor, text.length()));
        String beforeCursor = text.substring(1, cursor);
        String[] parts = beforeCursor.split("\\s+", -1);
        int tokenIndex = Math.max(0, parts.length - 1);
        String current = parts.length == 0 ? "" : parts[tokenIndex];

        if (tokenIndex == 0) {
            addCommandSuggestions(suggestions, current);
            return suggestions;
        }

        String commandName = parts[0];
        Command command = getCommand(commandName);
        Module moduleCommand = Unfair.moduleManager == null ? null : Unfair.moduleManager.getModule(commandName);

        if (isModuleArgumentCommand(command)) {
            if (tokenIndex == 1) {
                addModuleSuggestions(suggestions, current);
                if (isBindCommand(command)) {
                    addMatches(suggestions, Arrays.asList("*", "list", "l"), current);
                }
            } else if (tokenIndex == 2 && isBindCommand(command)) {
                addKeySuggestions(suggestions, current);
            }
        } else if (moduleCommand != null) {
            if (tokenIndex == 1) {
                addPropertySuggestions(suggestions, moduleCommand, current);
            } else if (tokenIndex == 2) {
                Property<?> property = Unfair.propertyManager == null ? null : Unfair.propertyManager.getProperty(moduleCommand, parts[1]);
                addPropertyValueSuggestions(suggestions, property, current);
            }
        } else if (isPlayerNameCommand(command)) {
            addPlayerSuggestions(suggestions, current);
        } else if (isConfigCommand(command)) {
            if (tokenIndex == 1) {
                addMatches(suggestions, Arrays.asList("load", "save", "list", "folder"), current);
            }
        }

        return suggestions;
    }

    private Command getCommand(String name) {
        if (name == null || Unfair.commandManager == null) {
            return null;
        }
        for (Command command : Unfair.commandManager.commands) {
            for (String alias : command.names) {
                if (alias.equalsIgnoreCase(name)) {
                    return command;
                }
            }
        }
        return null;
    }

    private void addCommandSuggestions(List<String> suggestions, String current) {
        Set<String> names = new LinkedHashSet<>();
        if (Unfair.commandManager != null) {
            for (Command command : Unfair.commandManager.commands) {
                names.addAll(command.names);
            }
        }
        if (Unfair.moduleManager != null) {
            for (Module module : Unfair.moduleManager.modules.values()) {
                names.add(module.getName());
            }
        }
        addMatches(suggestions, names, current);
    }

    private void addModuleSuggestions(List<String> suggestions, String current) {
        if (Unfair.moduleManager == null) {
            return;
        }
        ArrayList<String> names = new ArrayList<>();
        for (Module module : Unfair.moduleManager.modules.values()) {
            names.add(module.getName());
        }
        addMatches(suggestions, names, current);
    }

    private void addPropertySuggestions(List<String> suggestions, Module module, String current) {
        if (Unfair.propertyManager == null || module == null) {
            return;
        }
        List<Property<?>> properties = Unfair.propertyManager.properties.get(module.getClass());
        if (properties == null) {
            return;
        }
        ArrayList<String> names = new ArrayList<>();
        for (Property<?> property : properties) {
            if (property.isVisible()) {
                names.add(property.getName());
            }
        }
        addMatches(suggestions, names, current);
    }

    private void addPropertyValueSuggestions(List<String> suggestions, Property<?> property, String current) {
        if (property instanceof BooleanProperty) {
            addMatches(suggestions, Arrays.asList("true", "false", "on", "off"), current);
        } else if (property instanceof ModeProperty) {
            addMatches(suggestions, Arrays.asList(((ModeProperty) property).getDisplayModes()), current);
        }
    }

    private void addPlayerSuggestions(List<String> suggestions, String current) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            return;
        }
        ArrayList<String> names = new ArrayList<>();
        for (NetworkPlayerInfo info : mc.thePlayer.sendQueue.getPlayerInfoMap()) {
            if (info.getGameProfile() != null) {
                names.add(info.getGameProfile().getName());
            }
        }
        addMatches(suggestions, names, current);
    }

    private void addKeySuggestions(List<String> suggestions, String current) {
        ArrayList<String> names = new ArrayList<>(Arrays.asList(
                "NONE", "R", "F", "G", "H", "V", "B", "C", "X", "Z",
                "Q", "E", "T", "Y", "U", "I", "O", "P",
                "J", "K", "L", "N", "M",
                "LSHIFT", "LCONTROL", "LMENU", "SPACE", "TAB",
                "GRAVE", "CAPITAL", "RETURN", "BACK", "DELETE",
                "UP", "DOWN", "LEFT", "RIGHT",
                "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
        ));
        addMatches(suggestions, names, current);
    }

    private void addMatches(List<String> suggestions, Iterable<String> candidates, String current) {
        String normalizedCurrent = normalize(current);
        for (String candidate : candidates) {
            if (candidate != null && startsWithNormalized(candidate, normalizedCurrent) && !containsIgnoreCase(suggestions, candidate)) {
                suggestions.add(candidate);
            }
        }
        for (String candidate : candidates) {
            if (candidate != null && containsNormalized(candidate, normalizedCurrent) && !containsIgnoreCase(suggestions, candidate)) {
                suggestions.add(candidate);
            }
        }
    }

    private boolean startsWithNormalized(String candidate, String normalizedCurrent) {
        return normalizedCurrent.isEmpty() || normalize(candidate).startsWith(normalizedCurrent);
    }

    private boolean containsNormalized(String candidate, String normalizedCurrent) {
        return !normalizedCurrent.isEmpty() && normalize(candidate).contains(normalizedCurrent);
    }

    private boolean containsIgnoreCase(List<String> values, String value) {
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("-", "").replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private boolean isModuleArgumentCommand(Command command) {
        return hasName(command, "bind") || hasName(command, "toggle") || hasName(command, "hide") || hasName(command, "show");
    }

    private boolean isBindCommand(Command command) {
        return hasName(command, "bind");
    }

    private boolean isPlayerNameCommand(Command command) {
        return hasName(command, "friend") || hasName(command, "target") || hasName(command, "player") || hasName(command, "denick");
    }

    private boolean isConfigCommand(Command command) {
        return hasName(command, "config");
    }

    private boolean hasName(Command command, String name) {
        return command != null && command.names.stream().anyMatch(alias -> alias.equalsIgnoreCase(name));
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C01PacketChatMessage) {
            String msg = ((C01PacketChatMessage) event.getPacket()).getMessage();
            if (this.isTypingCommand(msg)) {
                event.setCancelled(true);
                this.handleCommand(msg);
            }
        }
    }
}

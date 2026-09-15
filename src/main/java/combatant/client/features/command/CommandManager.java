/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import combatant.client.runtime.discovery.CombatantIndex;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum CommandManager {
    ;
    public static final String PREFIX = "@";
    private static final List<ClientCommand> COMMANDS = new ArrayList<>();
    private static boolean initialized;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        discover("combatant.client.features.command.impl");
    }

    public static List<ClientCommand> getCommands() {
        if (!initialized) init();
        return Collections.unmodifiableList(COMMANDS);
    }

    public static void register(ClientCommand cmd) {
        if (cmd == null) return;
        CommandMetadata metadata = cmd.metadata();
        if (findRegistered(metadata.id()) != null) {
            throw new IllegalArgumentException("Duplicate client command: @" + metadata.id());
        }
        for (String alias : metadata.aliases()) {
            if (findRegistered(alias) != null) {
                throw new IllegalArgumentException("Duplicate client command alias: @" + alias);
            }
        }
        COMMANDS.add(cmd);
    }

    public static boolean handle(String raw) {
        if (raw == null) return false;
        int start = firstNonWhitespace(raw);
        if (start < 0) return false;
        if (!isCommandLike(raw)) return false;
        if (!initialized) init();

        int prefixLen = PREFIX == null ? 0 : PREFIX.length();
        int contentStart = start + prefixLen;
        if (contentStart > raw.length()) return false;
        String body = raw.substring(contentStart).trim();
        if (body.isEmpty()) return false;
        String[] parts = body.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return false;
        String name = parts[0].toLowerCase(java.util.Locale.ROOT);

        ClientCommand cmd = find(name);
        if (cmd == null) {
            CommandOutput.error("Unknown client command: " + PREFIX + name + ". Use " + PREFIX + "help.");
            return true;
        }
        if (!cmd.isAvailable()) {
            return true; // silently ignore when command is disabled
        }

        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(parts).subList(1, parts.length));
        CommandContext ctx = new CommandContext(Minecraft.getInstance(), raw, name, args);
        return cmd.execute(ctx);
    }

    public static List<Suggestion> suggest(String input, int cursor) {
        if (input == null) return List.of();
        int start = firstNonWhitespace(input);
        if (start < 0) return List.of();
        int prefixLen = PREFIX == null ? 0 : PREFIX.length();
        if (prefixLen > 0 && !input.startsWith(PREFIX, start)) return List.of();
        if (!initialized) init();

        int contentStart = start + prefixLen;
        if (cursor < contentStart) return List.of();
        int cursorClamped = Math.clamp(cursor, contentStart, input.length());
        if (contentStart > input.length() || contentStart > cursorClamped) return List.of();

        String before = input.substring(contentStart, cursorClamped);
        String[] parts = before.split("\\s+", -1);
        if (parts.length == 0) return List.of();

        int argIndex = parts.length - 1;
        String token = parts[argIndex];
        String cmdName = parts[0].toLowerCase(java.util.Locale.ROOT);

        int tokenStartInBefore = before.lastIndexOf(' ');
        tokenStartInBefore = tokenStartInBefore >= 0 ? tokenStartInBefore + 1 : 0;
        int tokenStartInInput = contentStart + tokenStartInBefore;
        int tokenEndInInput = tokenStartInInput + token.length();
        tokenStartInInput = Math.clamp(tokenStartInInput, 0, input.length());
        tokenEndInInput = Math.clamp(tokenEndInInput, tokenStartInInput, input.length());
        StringRange range = StringRange.between(tokenStartInInput, tokenEndInInput);

        List<String> suggestions;
        if (argIndex == 0) {
            suggestions = suggestCommandNames(token);
        } else {
            ClientCommand cmd = find(cmdName);
            if (cmd == null || !cmd.isAvailable()) return List.of();
            List<String> args = new ArrayList<>();
            args.addAll(Arrays.asList(parts).subList(1, parts.length));
            CommandContext ctx = new CommandContext(Minecraft.getInstance(), input, cmdName, args);
            suggestions = cmd.suggest(ctx, argIndex, token);
        }

        if (suggestions == null || suggestions.isEmpty()) return List.of();
        List<Suggestion> out = new ArrayList<>(suggestions.size());
        for (String s : suggestions) {
            if (s == null || s.isBlank()) continue;
            out.add(new Suggestion(range, s));
        }
        return out;
    }

    public static ClientCommand find(String name) {
        if (!initialized) init();
        return findRegistered(name);
    }

    private static ClientCommand findRegistered(String name) {
        if (name == null || name.isBlank()) return null;
        for (ClientCommand cmd : COMMANDS) {
            CommandMetadata metadata = cmd.metadata();
            if (metadata.id().equalsIgnoreCase(name)) return cmd;
            for (String alias : metadata.aliases()) {
                if (alias.equalsIgnoreCase(name)) return cmd;
            }
        }
        return null;
    }

    public static boolean isCommandLike(String text) {
        if (text == null || text.isEmpty()) return false;
        int start = firstNonWhitespace(text);
        if (start < 0) return false;
        if (PREFIX == null || PREFIX.isEmpty()) return true;
        return text.startsWith(PREFIX, start);
    }

    private static List<String> suggestCommandNames(String token) {
        String lower = token == null ? "" : token.toLowerCase();
        List<String> out = new ArrayList<>();
        for (ClientCommand cmd : COMMANDS) {
            if (!cmd.isAvailable()) continue;
            CommandMetadata metadata = cmd.metadata();
            if (lower.isEmpty() || metadata.id().startsWith(lower)) {
                out.add(metadata.id());
            }
            for (String alias : metadata.aliases()) {
                if (alias == null || alias.isBlank()) continue;
                if (lower.isEmpty() || alias.startsWith(lower)) {
                    out.add(alias);
                }
            }
        }
        return out;
    }

    private static int firstNonWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) return i;
        }
        return -1;
    }

    private static void discover(String basePackage) {
        CombatantIndex.classNames(CombatantIndex.Kind.COMMAND, basePackage)
                .forEach(CommandManager::loadCandidate);
    }

    private static void loadCandidate(String className) {
        try {
            Class<?> type = Class.forName(className, false, CommandManager.class.getClassLoader());
            if (!ClientCommand.class.isAssignableFrom(type)) {
                DebugLog.config("Skipping annotated non-command: %s", type.getName());
                return;
            }
            int modifiers = type.getModifiers();
            if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers) || type.isAnnotation()) {
                DebugLog.config("Skipping non-concrete command class: %s", type.getName());
                return;
            }
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            register((ClientCommand) constructor.newInstance());
            DebugLog.config("Loaded command: %s", type.getName());
        } catch (NoSuchMethodException exception) {
            DebugLog.error("Failed to load command %s: no no-args constructor", exception, className);
        } catch (Throwable throwable) {
            DebugLog.error("Failed to load command: %s", throwable, className);
        }
    }
}

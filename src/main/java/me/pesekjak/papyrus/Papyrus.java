package me.pesekjak.papyrus;

import com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource;
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.destroystokyo.paper.event.brigadier.CommandRegisteredEvent;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.google.common.base.Preconditions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Papyrus is a small modern library for creating Brigadier-based commands on Paper Minecraft servers.
 * <p>
 * It's written in one source file so plugins can include it as source and avoid adding a dependency.
 * It does not use reflection or NMS classes, making it future-proof and stable (as long as the Paper Mojang API does not change).
 * @see Command
 * @see CommandBuilder
 */
public final class Papyrus {

    private final JavaPlugin plugin;
    private final String fallback;
    private final ObjectOpenHashSet<Command> commands = new ObjectOpenHashSet<>();

    public Papyrus(@NotNull JavaPlugin plugin) {
        Preconditions.checkNotNull(plugin, "Papyrus requires a plugin instance to initialized");
        this.plugin = plugin;
        fallback = plugin.getName().toLowerCase(java.util.Locale.ENGLISH).trim();
        Bukkit.getPluginManager().registerEvents(new Listeners(), plugin);
    }

    /**
     * Registers new papyrus command to the server.
     * @param command command to register
     * @return if the command was successfully registered
     */
    public boolean register(@NotNull Command command) {
        if (!commands.add(command)) return false;
        return Bukkit.getCommandMap().register(fallback, command.getBukkit());
    }

    /**
     * Unregisters a papyrus command from the server.
     * @param command command to unregister
     * @return if the command was successfully unregistered
     * @deprecated commands removing is not supported by the API
     */
    @Deprecated
    public boolean unregister(@NotNull Command command) {
        if (!commands.remove(command)) return false;
        Collection<String> labels = getAllLabels(fallback, command);
        labels.forEach(label -> Bukkit.getCommandMap().getKnownCommands().remove(label));
        return true;
    }

    /**
     * Returns all registered commands by this papyrus instance.
     * @return all commands
     */
    public @Unmodifiable Collection<Command> getAll() {
        return Collections.unmodifiableCollection(commands);
    }

    /**
     * Represents a server command, combining both bukkit and brigadier APIs.
     * <p>
     * You can create your own commands as such:
     * <pre>
     * Command cmd = new Command("hello") {{
     *     aliases = new String[] { "world" };
     *     description = "My custom command";
     *     command.executes(context -> {
     *         System.out.println("Hello World");
     *         return com.mojang.brigadier.Command.SINGLE_SUCCESS;
     *     });
     * }};
     * </pre>
     * @see Papyrus#register(Command)
     */
    public static class Command {

        /**
         * Label of the command.
         */
        protected final String label;

        /**
         * Aliases of the command.
         */
        protected String @Nullable [] aliases;

        /**
         * Description of the command.
         */
        protected @Nullable String description;

        /**
         * Usage message of the command.
         */
        protected @Nullable String usageMessage;

        /**
         * Permission of the command.
         */
        protected @Nullable String permission;

        /**
         * Permission message of the command.
         */
        protected @Nullable Component permissionMessage;

        /**
         * Consumer that is accepted in case the command execution fails due to syntax exception.
         * <p>
         * In case the listener is not set and the command execution fails,
         * false is return at {@link org.bukkit.command.Command#execute(CommandSender, String, String[])}
         */
        protected @Nullable BiConsumer<BukkitBrigadierCommandSource, CommandSyntaxException> syntaxExceptionListener;

        /**
         * Command builder for the command.
         */
        protected final LiteralArgumentBuilder<BukkitBrigadierCommandSource> command;

        /**
         * Creates new command builder.
         * <p>
         * Builder is alternative to extending the command class, other than the design it works exactly the same.
         * @param label command label
         * @return new builder
         */
        public static CommandBuilder newBuilder(@NotNull String label) {
            return new CommandBuilder(label);
        }

        /**
         * New papyrus command.
         * @param label label of the command
         */
        public Command(@NotNull String label) {
            this.label = Preconditions.checkNotNull(label, "Command label can not be null");
            command = LiteralArgumentBuilder.literal(label);
        }

        /**
         * @return label of the command
         */
        public String getLabel() {
            return label;
        }

        /**
         * @return aliases of the command
         */
        public String[] getAliases() {
            return aliases != null ? aliases.clone() : new String[0];
        }

        /**
         * @return description of the command
         */
        public @Nullable String getDescription() {
            return description;
        }

        /**
         * @return usage message of the command
         */
        public @Nullable String getUsageMessage() {
            return usageMessage;
        }

        /**
         * @return permission of the command
         */
        public @Nullable String getPermission() {
            return permission;
        }

        /**
         * @return permission message of the command
         */
        public @Nullable Component getPermissionMessage() {
            return permissionMessage;
        }

        private CommandDispatcher<BukkitBrigadierCommandSource> dispatcher;
        private LiteralCommandNode<BukkitBrigadierCommandSource> brigadier;
        private org.bukkit.command.Command bukkit;

        private CommandDispatcher<BukkitBrigadierCommandSource> getDispatcher() {
            if (dispatcher != null) return dispatcher;
            dispatcher = new CommandDispatcher<>();
            dispatcher.getRoot().addChild(getBrigadier());
            return dispatcher;
        }

        private LiteralCommandNode<BukkitBrigadierCommandSource> getBrigadier() {
            if (brigadier != null) return brigadier;
            brigadier = command.build();
            return brigadier;
        }

        private org.bukkit.command.Command getBukkit() {
            if (bukkit != null) return bukkit;

            String description = getDescription() != null ? getDescription() : "";
            String usageMessage = getUsageMessage() != null ? getUsageMessage() : "/" + getLabel();

            bukkit = new org.bukkit.command.Command(getLabel(), description, usageMessage, List.of(getAliases())) {
                @Override
                public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
                    String fullCommand = Command.this.getLabel() + (args.length != 0 ? " " + String.join(" ", args) : "");
                    BukkitBrigadierCommandSource source = new CommandSourceWrapper(sender);
                    try {
                        getDispatcher().execute(fullCommand, source);
                        return true;
                    } catch (CommandSyntaxException exception) {
                        if (Command.this.syntaxExceptionListener == null) return false;
                        Command.this.syntaxExceptionListener.accept(source, exception);
                        return true;
                    }
                }
            };

            bukkit.setPermission(getPermission());
            bukkit.permissionMessage(getPermissionMessage());
            return bukkit;
        }

    }

    /**
     * Builder is alternative to extending the command class.
     * @see Command#newBuilder(String)
     */
    public static final class CommandBuilder {

        private final Command command;

        CommandBuilder(@NotNull String label) {
            command = new Command(label);
        }

        /**
         * Sets new aliases for the command.
         * @param aliases new aliases
         * @return builder
         */
        public CommandBuilder setAliases(@NotNull String... aliases) {
            command.aliases = aliases;
            return this;
        }

        /**
         * Sets new aliases for the command.
         * @param aliases new aliases
         * @return builder
         */
        public CommandBuilder setAliases(@NotNull Collection<String> aliases) {
            return setAliases(aliases.toArray(new String[0]));
        }

        /**
         * Sets new description for the command.
         * @param description new description
         * @return builder
         */
        public CommandBuilder setDescription(@Nullable String description) {
            command.description = description;
            return this;
        }

        /**
         * Usage message of the command.
         * @param usageMessage new usage message
         * @return builder
         */
        public CommandBuilder setUsageMessage(@Nullable String usageMessage) {
            command.usageMessage = usageMessage;
            return this;
        }

        /**
         * Sets new permission for the command.
         * @param permission new permission
         * @return builder
         */
        public CommandBuilder setPermission(@Nullable String permission) {
            command.permission = permission;
            return this;
        }

        /**
         * Sets new permission message for the command.
         * @param permissionMessage new permission message
         * @return builder
         */
        public CommandBuilder setPermissionMessage(@Nullable Component permissionMessage) {
            command.permissionMessage = permissionMessage;
            return this;
        }

        /**
         * Consumer that is accepted in case the command execution fails due to syntax exception.
         * <p>
         * In case the listener is not set and the command execution fails,
         * false is return at {@link org.bukkit.command.Command#execute(CommandSender, String, String[])}
         * @param listener new syntax exception listener
         * @return builder
         */
        public CommandBuilder setSyntaxExceptionListener(@Nullable BiConsumer<BukkitBrigadierCommandSource, CommandSyntaxException> listener) {
            command.syntaxExceptionListener = listener;
            return this;
        }

        /**
         * Creates logic for the command.
         * @param command consumer
         * @return builder
         */
        public CommandBuilder createLogic(@NotNull Consumer<LiteralArgumentBuilder<BukkitBrigadierCommandSource>> command) {
            command.accept(this.command.command);
            return this;
        }

        /**
         * Builds the command of this builder.
         * @return command
         */
        public Command build() {
            return command;
        }

    }

    class Listeners implements Listener {

        @EventHandler
        @SuppressWarnings("UnstableApiUsage")
        private void onCommandRegistered(CommandRegisteredEvent<BukkitBrigadierCommandSource> event) {
            Command command = commands.stream().filter(c -> c.getLabel().equalsIgnoreCase(event.getCommand().getLabel())).findAny().orElse(null);
            if (command == null) return;
            event.setRawCommand(true);
            event.setLiteral(buildRedirect(event.getCommandLabel(), command.getBrigadier()));
        }

        @EventHandler
        private void onPlayerSendSuggestionsEvent(AsyncPlayerSendSuggestionsEvent event) {
            String buffer = event.getBuffer();
            String[] parts = buffer.split(" ");

            String commandName = parts[0].substring(1);
            String label = Optional.ofNullable(Bukkit.getCommandMap().getCommand(commandName)).map(org.bukkit.command.Command::getLabel).orElse(null);

            if (label == null) return;

            Command command = commands.stream().filter(c -> c.getLabel().equalsIgnoreCase(label)).findAny().orElse(null);
            if (command == null) return;

            BukkitBrigadierCommandSource source = new CommandSourceWrapper(event.getPlayer());
            String toParse = label + buffer.substring(parts[0].length());
            ParseResults<BukkitBrigadierCommandSource> parseResults = command.getDispatcher().parse(toParse, source);

            try {
                Suggestions suggestions = command.getDispatcher().getCompletionSuggestions(parseResults).get();
                event.setSuggestions(new Suggestions(StringRange.at(buffer.length()), suggestions.getList()));
            } catch (Exception exception) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        private void onAsyncTabComplete(AsyncTabCompleteEvent event) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> event.setHandled(true));
        }

    }

    private static <T> LiteralCommandNode<T> buildRedirect(String alias, LiteralCommandNode<T> destination) {
        LiteralArgumentBuilder<T> builder = LiteralArgumentBuilder
                .<T>literal(alias.toLowerCase())
                .requires(destination.getRequirement())
                .forward(destination.getRedirect(), destination.getRedirectModifier(), destination.isFork())
                .executes(destination.getCommand());
        for (CommandNode<T> child : destination.getChildren())
            builder.then(child);
        return builder.build();
    }

    private static Collection<String> getAllLabels(String fallback, Command command) {
        List<String> labels = new ArrayList<>();
        labels.add(command.getLabel());
        labels.add(fallback + ":" + command.getLabel());
        Arrays.stream(command.getAliases()).forEach(alias -> {
            labels.add(alias);
            labels.add(fallback + ":" + alias);
        });
        return labels.stream().map(label -> label.toLowerCase(Locale.ENGLISH).trim()).toList();
    }

    private record CommandSourceWrapper(CommandSender sender) implements BukkitBrigadierCommandSource {

        @Override
        public @Nullable Entity getBukkitEntity() {
            return sender instanceof Entity entity ? entity : null;
        }

        @Override
        public @Nullable World getBukkitWorld() {
            if (sender instanceof Entity entity) return entity.getWorld();
            if (sender instanceof BlockCommandSender block) return block.getBlock().getWorld();
            return null;
        }

        @Override
        public @Nullable Location getBukkitLocation() {
            if (sender instanceof Entity entity) return entity.getLocation();
            if (sender instanceof BlockCommandSender block) return block.getBlock().getLocation();
            return null;
        }

        @Override
        public CommandSender getBukkitSender() {
            return sender;
        }

    }

}

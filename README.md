# Papyrus
**A Minecraft Command library for Paper servers.**

[![license](https://img.shields.io/github/license/pesekjak/papyrus?style=for-the-badge&color=657185)](LICENSE)

Papyrus is a small modern library for creating commands on Paper Minecraft servers supporting new
command UI features introduced in Minecraft 1.13.
It's written in one source file so plugins can include it as source and avoid adding a dependency,
and it does not use reflection or NMS classes, making it future-proof and stable.

Information on how to [import](#import) this library can be found at the end of this file.

---

## Features

* Full support of Mojang brigadier library
* Support to specify Bukkit command information (description, permissions etc.)
* Commands do not have to be registered in `plugin.yml`
* Prevents players from running invalid commands not matching the required arguments
* Allows you to customize result of failed command executions due to syntax errors
* Full support for customizing argument tooltips
* Modern and easy to use design

## Usage

For each command you can specify the same properties as you do in `plugin.yml`, the logic
is implemented using brigadier.

Papyrus offers two ways of creating commands, it's possible to extend the `Command` class or
use the `CommandBuilder` instead.

```java
Papyrus.Command myCommand = new Papyrus.Command("hello") {{
    description = "My custom command";
    usageMessage = "/hello";
    command.executes(context -> {
        context.getSource().getBukkitSender().sendMessage("Hello World!");
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    });
}};

Papyrus.Command myOtherCommand = Papyrus.Command.newBuilder("hi")
        .setDescription("My second custom command")
        .setUsageMessage("/hi")
        .createLogic(command -> {
            command.executes(context -> {
                context.getSource().getBukkitSender().sendMessage("Hi World!");
                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
            });
        })
        .build();
```
Both options offer the same level of customization and features, the difference is only in
design.

To register the commands, you first need to create new instance of Papyrus using your java plugin,
then you use it to register the commands to the server.

```java
Papyrus papyrus = new Papyrus(plugin);
papyrus.register(myCommand);
papyrus.register(myOtherCommand);
```

## Import

To use the library you need to add Paper's Mojang API and Brigadier to your project.
```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://libraries.minecraft.net")
}

dependencies {
    implementation("io.papermc.paper:paper-mojangapi:VERSION")
    implementation("com.mojang:brigadier:VERSION")
}
```
Then you can copy and paste the Papyrus [source file](src/main/java/me/pesekjak/papyrus/Papyrus.java)
to your own projects and use it freely.
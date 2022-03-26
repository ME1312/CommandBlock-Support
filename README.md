# CommandBlock Support
[![Build Status](https://dev.me1312.net/jenkins/job/CommandBlock%20Support/badge/icon)](https://dev.me1312.net/jenkins/job/CommandBlock%20Support/) 
[![Release Verison](https://img.shields.io/github/release/ME1312/CommandBlock-Support/all.svg)](https://github.com/ME1312/CommandBlock-Support/releases)<br><br>
CommandBlock Support (CBS) lets you use commands that are normally only available to players in Command Blocks.<br>
> [https://www.spigotmc.org/resources/commandblock-support.xxxxx/](https://github.com/ME1312/CommandBlock-Support/wiki/)


## Quick Links
These are some quick links for common resources of CBS.

### Flag Usage
> [https://github.com/ME1312/CommandBlock-Support/wiki/Flags](https://github.com/ME1312/CommandBlock-Support/wiki/Flags)

### Snapshot Downloads
> [https://dev.me1312.net/jenkins/job/CommandBlock Support](https://dev.me1312.net/jenkins/job/CommandBlock%20Support)

### Stats for nerds
> [https://bstats.org/plugin/bungeecord/CommandBlock_Support](https://bstats.org/plugin/bukkit/CommandBlock%20Support)<br>


## Contributing Translations
If you've found a command that doesn't work with this plugin, and you have what it takes to solve that problem, then let this section be your guide in your quest to create a pull request.<br>

**Step 1:** Figure out which methods are being called.
This can be done with ease by re-running `/cbs` with the helpful debugging flag `-d` and reading the console output.
Pay extra attention to methods that report they are *untranslated*, as that means they are returning default values such as `null` (or `0` for primitive types).<br>

**Step 2:** Translate those problematic methods.
This is done by placing a method with the same name and parameters into [`EmulatedPlayer`](https://github.com/ME1312/CommandBlock-Support/blob/master/src/net/ME1312/CBS/EmulatedPlayer.java).
It's functionally the same as overriding a method from bukkit's [`Player`](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/entity/Player.html) class.

**Step 3:** Repeat this process until it starts working.
If you are having trouble and in need of more debugging information regarding how the plugin makes translations, start your server with the JVM flag `-Dcbs.debug=true`.
This will output the status of every detected method to the console and output the generated class to our plugin directory for bytecode inspection/decompilation.<br>

**Step 4:** Share your improvements with the world &ndash; send us your pull request!
The server owners trying to do that thing that you wanted to do with those plugins you're using will thank you.
<br><br>

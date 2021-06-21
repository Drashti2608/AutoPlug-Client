/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.tasks.updater.plugins.DetailedPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.PluginManager;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.exceptions.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PluginsConfig extends DreamYaml {
    @NotNull
    private final List<DetailedPlugin> includedPlugins = new ArrayList<>();
    @NotNull
    private final List<DetailedPlugin> allPlugins = new ArrayList<>();
    @NotNull
    private final List<DetailedPlugin> excludedPlugins = new ArrayList<>();


    public DYModule keep_removed;

    public PluginsConfig() throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug-plugins-config.yml");
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Plugins-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################\n" +
                        "This file contains detailed information about your installed plugins. It is fetched from each plugins 'plugin.yml' file (located inside its jar).\n" +
                        "The data gets refreshed before performing an update-check. To exclude a plugin from the check set exclude=true.\n" +
                        "If a name/author/version is missing, the plugin gets excluded automatically.\n" +
                        "You can add extra information by defining an id (spigot or bukkit) and a custom link (optional & must be a static link to the latest plugin jar).\n" +
                        "The spigot-id of a plugin, can be found directly in the url.\n" +
                        "Example URL: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "The spigot-id from the URL above, is therefore 78414.\n" +
                        "For the bukkit-id (or project-id) you need to visit the plugins bukkit site and read it from the box at the right.\n" +
                        "If a spigot-id is not given, AutoPlug will try and find the matching id by using its unique search-algorithm (if it succeeds the spigot-id gets set, else it stays 0).\n" +
                        "If both (bukkit and spigot) ids are provided, the spigot-id will be used.\n" +
                        "The configuration for uninstalled plugins wont be removed from this file, but they are automatically excluded from future checks (the exclude value is ignored).\n" +
                        "If multiple authors are provided, only the first author will be used by the search-algorithm.\n" +
                        "Note: Remember, that the values for exclude, version and author get overwritten if new data is available.\n" +
                        "Note for plugin devs: You can add your spigot/bukkit-id to your plugin.yml file. For more information visit " + GD.OFFICIAL_WEBSITE + "faq/2");

        keep_removed = put(name, "general", "keep-removed").setDefValues("true")
                .setComments("Keep the plugins entry in this file even after its removal/uninstallation?");

        PluginManager man = new PluginManager();
        this.allPlugins.addAll(man.getPlugins());
        if (!allPlugins.isEmpty())
            for (DetailedPlugin pl :
                    allPlugins) {

                final String plName = pl.getName();

                DYModule exclude = put(name, plName, "exclude").setDefValues("false"); // Check this plugin?
                DYModule version = put(name, plName, "version").setDefValues(pl.getVersion());
                DYModule latestVersion = put(name, plName, "latest-version");
                DYModule authors = put(name, plName, "author").setDefValues(pl.getAuthor());
                DYModule spigotId = put(name, plName, "spigot-id").setDefValues("0");
                //DYModule songodaId = new DYModule(config, getModules(), name, plName,+".songoda-id", 0); // TODO WORK_IN_PROGRESS
                DYModule bukkitId = put(name, plName, "bukkit-id").setDefValues("0");
                DYModule customLink = put(name, plName, "c-link");

                if ((pl.getVersion() == null || pl.getVersion().isEmpty()) ||
                        (pl.getAuthor() == null || pl.getAuthor().isEmpty())) {
                    exclude.setValues("true");
                    AL.warn("Plugin " + pl.getName() + " is missing critical information and was excluded.");
                }

                if (pl.getSpigotId() != 0 && spigotId.asString() != null && spigotId.asInt() == 0) // Don't update the value, if the user has already set it
                    spigotId.setValues("" + pl.getSpigotId());
                if (pl.getBukkitId() != 0 && bukkitId.asString() != null && bukkitId.asInt() == 0)
                    bukkitId.setValues("" + pl.getBukkitId());

                pl.setSpigotId(spigotId.asInt());
                pl.setBukkitId(bukkitId.asInt());
                pl.setCustomLink(customLink.asString());


                if (!exclude.asBoolean())
                    includedPlugins.add(pl);
                else
                    excludedPlugins.add(pl);
            }

        if (keep_removed.asBoolean())
            save();
        else
            save(true); // This overwrites the file and removes everything else that wasn't added via the add method before.
    }

    /**
     * Returns a list containing only plugins, that contain all the information needed to perform a search. <br>
     * That means, that a plugin must have its name, its authors name and its version in its plugin.yml file.
     */
    @NotNull
    public List<DetailedPlugin> getIncludedPlugins() {
        return includedPlugins;
    }

    @NotNull
    public List<DetailedPlugin> getExcludedPlugins() {
        return excludedPlugins;
    }

    /**
     * Returns a list containing all plugins found in the /plugins directory. <br>
     */
    @NotNull
    public List<DetailedPlugin> getAllPlugins() {
        return allPlugins;
    }
}

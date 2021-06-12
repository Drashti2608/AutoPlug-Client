/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.PluginsConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.network.online.connections.PluginsUpdaterConnection;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class TaskPluginsUpdater extends BetterThread {
    private final PluginsUpdaterConnection con;
    private final String notifyProfile = "NOTIFY";
    private final String manualProfile = "MANUAL";
    private final String automaticProfile = "AUTOMATIC";
    private UpdaterConfig updaterConfig;
    private String userProfile;
    private PluginsConfig pluginsConfig;
    private Socket online_socket;
    private DataInputStream online_dis;
    private DataOutputStream online_dos;
    private int updatesAvailable = 0;
    private int updatesDownloaded = 0;

    public TaskPluginsUpdater(String name, BetterThreadManager manager, PluginsUpdaterConnection con) {
        super(name, manager);
        this.con = con;
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        try {
            pluginsConfig = new PluginsConfig();
        } catch (DuplicateKeyException e) {
            getWarnings().add(new BetterWarning(this, e, "Duplicate plugin (or plugin name from its plugin.yml) found in your plugins directory. " +
                    "Remove it and restart AutoPlug."));
            setSuccess(false);
            return;
        }
        updaterConfig = new UpdaterConfig();
        userProfile = updaterConfig.plugin_updater_profile.asString();

        DetailedPlugin currentPl = null; // Used for exception details
        try { // Create this try/catch only for being able to close the connection
            this.setAutoFinish(false); // So that the last finish message is shown.

            if (!updaterConfig.plugin_updater.asBoolean()) {
                skip();
                return;
            }
            if (Server.isRunning()) throw new Exception("Cannot perform plugins update while server is running!");
            if (!con.isConnected()) con.open(); // Throws exception if auth failed

            online_socket = con.getSocket();
            online_dis = new DataInputStream(con.getIn());
            online_dos = new DataOutputStream(con.getOut());

            long msLeft = online_dis.readLong(); // 0 if the last plugins check was over 4 hours ago, else it returns the time left, till a new check is allowed
            if (msLeft != 0) {
                skip("Skipped. Cool-down still active (" + (msLeft / 60000) + " minutes remaining).");
                return;
            }

            // First we get the latest plugin details from the yml config.
            // The minimum required information is:
            // name, version, and author. Otherwise they won't get update-checked by AutoPlug (and are not inside the list below).
            setStatus("Fetching latest plugin data...");
            List<DetailedPlugin> plugins = pluginsConfig.getDetailedPlugins();
            int size = plugins.size();
            setMax(size);

            online_dos.writeInt(size);

            if (size == 0) throw new Exception("Plugins size is 0! Nothing to check...");

            // The yml config lets users define the spigot id,
            // bukkit id and custom link for the plugin.
            // If none of that information is given the AutoPlug algorithm will try and find the right version.
            List<DetailedPlugin> spigotIdPlugins = new ArrayList<>();
            List<DetailedPlugin> bukkitIdPlugins = new ArrayList<>();
            List<DetailedPlugin> customLinkPlugins = new ArrayList<>();
            List<DetailedPlugin> unknownPlugins = new ArrayList<>();

            // Add each plugin to their respective list
            for (DetailedPlugin pl :
                    plugins) {
                if (pl.getSpigotId() != 0) spigotIdPlugins.add(pl);
                else if (pl.getBukkitId() != 0) bukkitIdPlugins.add(pl);
                else if (pl.getCustomLink() != null && !pl.getCustomLink().isEmpty()) customLinkPlugins.add(pl);
                else unknownPlugins.add(pl);
            }

            int sizeSpigotPlugins = spigotIdPlugins.size();
            int sizeBukkitPlugins = bukkitIdPlugins.size();
            int sizeCustomLinkPlugins = customLinkPlugins.size();
            int sizeUnknownPlugins = unknownPlugins.size();

            online_dos.writeInt(sizeSpigotPlugins);
            online_dos.writeInt(sizeBukkitPlugins);
            online_dos.writeInt(sizeCustomLinkPlugins);
            online_dos.writeInt(sizeUnknownPlugins);

            byte code;
            String type; // The file type to download (Note: When 'external' is returned nothing will be downloaded. Working on a fix for this!)
            String latest; // The latest version as String
            String url; // The download url for the latest version
            String resultSpigotId;
            String resultBukkitId;


            for (DetailedPlugin pl :
                    spigotIdPlugins) {
                currentPl = pl;
                setStatus("Checking " + pl.getName() + "(" + step() + "/" + size + ") for updates...");
                online_dos.writeUTF(pl.getName());
                online_dos.writeUTF(pl.getVersion());
                online_dos.writeUTF(pl.getAuthor());
                online_dos.writeInt(pl.getSpigotId());

                code = online_dis.readByte();
                if (code == 0 || code == 1) {
                    type = online_dis.readUTF();
                    latest = online_dis.readUTF();
                    url = online_dis.readUTF();
                    resultSpigotId = online_dis.readUTF();
                    resultBukkitId = online_dis.readUTF();

                    doDownloadLogic(pl, code, type, latest, url, resultSpigotId, resultBukkitId);
                } else if (code == 2)
                    getWarnings().add(new BetterWarning(this, new Exception("Plugin " + pl.getName() + " was not found by the search-algorithm!"), "Specify an id in the plugins config file."));
                else if (code == 3)
                    getWarnings().add(new BetterWarning(this, new Exception("There was an api-error for " + pl.getName() + "!")));
                else
                    getWarnings().add(new BetterWarning(this, new Exception("Unknown error occurred! Code: " + code + "."), "Notify the developers. Fastest way is through discord (https://discord.gg/GGNmtCC)."));

            }

            for (DetailedPlugin pl :
                    bukkitIdPlugins) {
                currentPl = pl;
                setStatus("Checking " + pl.getName() + "(" + step() + "/" + size + ") for updates...");
                online_dos.writeUTF(pl.getName());
                online_dos.writeUTF(pl.getVersion());
                online_dos.writeUTF(pl.getAuthor());
                online_dos.writeInt(pl.getBukkitId());

                code = online_dis.readByte();
                if (code == 0 || code == 1) {
                    type = online_dis.readUTF();
                    latest = online_dis.readUTF();
                    url = online_dis.readUTF();
                    resultSpigotId = online_dis.readUTF();
                    resultBukkitId = online_dis.readUTF();

                    doDownloadLogic(pl, code, type, latest, url, resultSpigotId, resultBukkitId);
                } else if (code == 2)
                    getWarnings().add(new BetterWarning(this, new Exception("Plugin " + pl.getName() + " was not found by the search-algorithm!"), "Specify an id in the autoplug-plugins-config.yml."));
                else if (code == 3)
                    getWarnings().add(new BetterWarning(this, new Exception("There was an api-error for " + pl.getName() + "!")));
                else
                    getWarnings().add(new BetterWarning(this, new Exception("Unknown error occurred! Code: " + code + "."), "Notify the developers. Fastest way is through discord (https://discord.gg/GGNmtCC)."));
            }

            for (DetailedPlugin pl :
                    customLinkPlugins) {
                currentPl = pl;
                setStatus("Checking " + pl.getName() + "(" + step() + "/" + size + ") for updates...");
                online_dos.writeUTF(pl.getName());
                online_dos.writeUTF(pl.getVersion());
                online_dos.writeUTF(pl.getAuthor());
                online_dos.writeInt(pl.getSpigotId());
                online_dos.writeInt(pl.getBukkitId());

                code = online_dis.readByte();
                if (code == 0 || code == 1) {
                    type = online_dis.readUTF();
                    latest = online_dis.readUTF();
                    url = online_dis.readUTF();
                    resultSpigotId = online_dis.readUTF();
                    resultBukkitId = online_dis.readUTF();

                    doDownloadLogic(pl, code, type, latest, url, resultSpigotId, resultBukkitId);
                } else if (code == 2)
                    getWarnings().add(new BetterWarning(this, new Exception("Plugin " + pl.getName() + " was not found by the search-algorithm!"), "Specify an id in the autoplug-plugins-config.yml."));
                else if (code == 3)
                    getWarnings().add(new BetterWarning(this, new Exception("There was an api-error for " + pl.getName() + "!")));
                else
                    getWarnings().add(new BetterWarning(this, new Exception("Unknown error occurred! Code: " + code + "."), "Notify the developers. Fastest way is through discord (https://discord.gg/GGNmtCC)."));
            }

            for (DetailedPlugin pl :
                    unknownPlugins) {
                currentPl = pl;
                setStatus("Checking " + pl.getName() + "(" + step() + "/" + size + ") for updates...");
                online_dos.writeUTF(pl.getName());
                online_dos.writeUTF(pl.getVersion());
                online_dos.writeUTF(pl.getAuthor());

                code = online_dis.readByte();
                if (code == 0 || code == 1) {
                    type = online_dis.readUTF();
                    latest = online_dis.readUTF();
                    url = online_dis.readUTF();
                    resultSpigotId = online_dis.readUTF();
                    resultBukkitId = online_dis.readUTF();

                    doDownloadLogic(pl, code, type, latest, url, resultSpigotId, resultBukkitId);
                } else if (code == 2)
                    getWarnings().add(new BetterWarning(this, new Exception("Plugin " + pl.getName() + " was not found by the search-algorithm!"), "Specify an id in the autoplug-plugins-config.yml."));
                else if (code == 3)
                    getWarnings().add(new BetterWarning(this, new Exception("There was an api-error for " + pl.getName() + "!")));
                else
                    getWarnings().add(new BetterWarning(this, new Exception("Unknown error occurred! Code: " + code + "."), "Notify the developers. Fastest way is through discord (https://discord.gg/GGNmtCC)."));

            }

            // Save the config
            pluginsConfig.save();
            finish("Checked " + size + " plugins and found " + updatesAvailable + " updates!");
        } catch (Exception e) {
            // Create this try/catch only for being able to close the connection
            // and rethrow this exception so the Thread finishes
            con.close();
            if (currentPl != null)
                getWarnings().add(new BetterWarning(this, new Exception("Critical error which aborted the plugins updater, while checking plugin: " + currentPl.getName() + "(" + currentPl.getVersion() + ") from " + currentPl.getInstallationPath())));
            throw e;
        }

    }

    private void doDownloadLogic(@NotNull DetailedPlugin pl, byte code, @NotNull String type, String latest, String url, @NotNull String resultSpigotId, @NotNull String resultBukkitId) {
        if (code == 0) {
            //getSummary().add("Plugin " +pl.getName()+ " is already on the latest version (" + pl.getVersion() + ")"); // Only for testing right now
        } else {
            updatesAvailable++;
            getSummary().add("Plugin " + pl.getName() + " has an update available (" + pl.getVersion() + " -> " + latest + ")");
            try {
                // Update the in-memory config
                DYModule mLatest = pluginsConfig.get(pluginsConfig.getFileNameWithoutExt(), pl.getName(), "latest-version");
                mLatest.setValues(latest);

                DYModule mSpigotId = pluginsConfig.get(pluginsConfig.getFileNameWithoutExt(), pl.getName(), "spigot-id");
                if (!resultSpigotId.equals("null")) // Because we can get a "null" string from the server
                    mSpigotId.setValues(resultSpigotId);

                DYModule mBukkitId = pluginsConfig.get(pluginsConfig.getFileNameWithoutExt(), pl.getName(), "bukkit-id");
                if (!resultBukkitId.equals("null")) // Because we can get a "null" string from the server
                    mBukkitId.setValues(resultBukkitId);

                // The config gets saved at the end of the runAtStart method.
            } catch (Exception e) {
                getWarnings().add(new BetterWarning(this, e));
            }


            if (userProfile.equals(notifyProfile)) {
                // Do nothing more
            } else {
                if (type.equals(".jar") || type.equals("external")) { // Note that "external" support is kind off random and strongly dependent on what spigot devs are doing
                    if (userProfile.equals(manualProfile)) {
                        File cache_dest = new File(GD.WORKING_DIR + "/autoplug-downloads/" + pl.getName() + "[" + latest + "].jar");
                        new TaskPluginDownload("PluginDownloader", getManager(), pl.getName(), latest, url, userProfile, cache_dest)
                                .start();
                        updatesDownloaded++;
                    } else {
                        File oldPl = new File(pl.getInstallationPath());
                        File dest = new File(GD.WORKING_DIR + "/plugins/" + pl.getName() + "-LATEST-" + "[" + latest + "]" + ".jar");
                        new TaskPluginDownload("PluginDownloader", getManager(), pl.getName(), latest, url, userProfile, dest, oldPl)
                                .start();
                        updatesDownloaded++;
                    }
                } else
                    getWarnings().add(new BetterWarning(this, new Exception("Failed to download plugin update(" + latest + ") for " + pl.getName() + " because of unsupported type: " + type)));
            }
        }

    }

}

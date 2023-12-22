/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.dyml.Yaml;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.dyml.exceptions.IllegalListException;
import com.osiris.dyml.exceptions.YamlReaderException;
import com.osiris.dyml.exceptions.YamlWriterException;
import com.osiris.dyml.watcher.FileEvent;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class MyYaml extends Yaml {
    public MyYaml(InputStream inputStream, OutputStream outputStream) {
        super(inputStream, outputStream);
    }

    public MyYaml(InputStream inputStream, OutputStream outputStream, boolean isDebugEnabled) {
        super(inputStream, outputStream, isDebugEnabled);
    }

    public MyYaml(InputStream inputStream, OutputStream outputStream, boolean isPostProcessingEnabled, boolean isDebugEnabled) {
        super(inputStream, outputStream, isPostProcessingEnabled, isDebugEnabled);
    }

    public MyYaml(String inString, String outString) {
        super(inString, outString);
    }

    public MyYaml(String inString, String outString, boolean isDebugEnabled) {
        super(inString, outString, isDebugEnabled);
    }

    public MyYaml(String inString, String outString, boolean isPostProcessingEnabled, boolean isDebugEnabled) {
        super(inString, outString, isPostProcessingEnabled, isDebugEnabled);
    }

    public MyYaml(File file) {
        super(file);
    }

    public MyYaml(File file, boolean isDebugEnabled) {
        super(file, isDebugEnabled);
    }

    public MyYaml(File file, boolean isPostProcessingEnabled, boolean isDebugEnabled) {
        super(file, isPostProcessingEnabled, isDebugEnabled);
    }

    public MyYaml(String filePath) {
        super(filePath);
    }

    public MyYaml(String filePath, boolean isDebugEnabled) {
        super(filePath, isDebugEnabled);
    }

    public MyYaml(String filePath, boolean isPostProcessingEnabled, boolean isDebugEnabled) {
        super(filePath, isPostProcessingEnabled, isDebugEnabled);
    }

    /**
     * Pairs of absolute file path and count of pending programmatic save events for that file.
     */
    private static final Map<String, PSave> filesAndPEvents = new HashMap<>();

    private long msLastEvent = 0;

    /**
     * Does nothing if a listener for this config/file was already registered. <br>
     * {@link #load()} is performed before executing the listener to ensure this config has the latest values
     * and {@link #validateValues()} to ensure the input is correct. <br>
     * <p>
     * Also has anti-spam meaning it waits 10 seconds for a newer event and executes the listener then. <br>
     * <p>
     * To be able to call {@link #save(boolean)} in here and prevent a stackoverflow error, events
     * caused by a programmatic save will not execute the listener, thus only user modify events will.
     */
    public Yaml addSingletonConfigFileEventListener(Consumer<FileEvent> listener) throws IOException {
        if (file == null) throw new RuntimeException("file cannot be null.");
        String path = file.getAbsolutePath();

        synchronized (filesAndPEvents) {
            if (filesAndPEvents.containsKey(path)) return this; // Already exists
            filesAndPEvents.put(path, new PSave());
        }

        super.addFileEventListener(e -> {
            String preInfo = "Modified " + this.file.getName() + ": ";
            if (e.isDeleteEvent())
                AL.info(preInfo + "Deleted. Thus clean config with defaults will be created once ist needed.");
            if (e.isModifyEvent()) {
                synchronized (filesAndPEvents) {
                    PSave p = filesAndPEvents.get(path);
                    //AL.info(preInfo+" pending: "+p.pendingSaveCount);
                    if (p.pendingSaveCount != 0) {
                        p.pendingSaveCount--;
                        if (p.pendingSaveCount < 0) // Prevent negative values
                            p.pendingSaveCount = 0;
                        // Prevent stackoverflow from recursive programmatic save events.
                        // Only allow user save events.
                        return;
                    }
                }
                long msThisEvent = System.currentTimeMillis();
                new Thread(() -> {
                    try {
                        Thread.sleep(10000); // 10sec
                        if (msLastEvent > msThisEvent)
                            // Means there was a newer event than this one
                            return;
                        try {
                            lockFile();
                            load();
                        } catch (Exception ex) {
                            AL.warn(preInfo + "Failed to update internal values for config. Check for syntax errors.", ex);
                            return;
                        } finally {
                            unlockFile();
                        }
                        try {
                            validateValues();
                        } catch (Exception ex) {
                            AL.warn(preInfo + "Failed to update internal values for config. One or multiple values are not valid.", ex);
                            return;
                        }
                        listener.accept(e);
                        AL.info(preInfo + "Modified. Internal values updated.");
                    } catch (Exception ex) {
                        AL.warn(ex);
                    }
                }).start();
                msLastEvent = msThisEvent;
            }
        });

        return this;
    }

    public abstract Yaml validateValues();

    @Override
    public Yaml save(boolean overwrite) throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException {
        validateValues();
        synchronized (filesAndPEvents) {
            String path = file.getAbsolutePath();
            if (filesAndPEvents.containsKey(path)) {
                PSave p = filesAndPEvents.get(path);
                // If save events are to close after each other the listener fails to differentiate them
                // and only notices one event, thus to prevent incrementing the count by adding a delay between saves
                long msCurrentSave = System.currentTimeMillis();
                //AL.info(""+ (msCurrentSave - p.msLastSave));
                if (msCurrentSave - p.msLastSave >= 50) {
                    //AL.info("save() "+this.file.getName()+" filesAndPEvents.get(p) + 1 = "+ (p.pendingSaveCount + 1));
                    p.pendingSaveCount++;
                }
                p.msLastSave = msCurrentSave;
            }
        }
        return super.save(overwrite);
    }

    /**
     * Programmatic save of a yaml file.
     */
    public static class PSave {
        public long msLastSave = System.currentTimeMillis();
        public int pendingSaveCount = 0;
    }
}

/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.console.AutoPlugConsole;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.core.logger.AL;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * The user can send commands through the online console.<br>
 * For that we got this connection, which listens for the user
 * input at the online console and executes it.
 */
public class OnlineConsoleReceiveConnection extends SecondaryConnection {
    @Nullable
    private static Thread thread;

    public OnlineConsoleReceiveConnection() {
        super((byte) 1);
    }

    @Override
    public boolean open() throws Exception {
        super.open();
        if (thread == null)
            thread = new Thread(() -> {
                try {
                    getSocket().setSoTimeout(0);
                    DataInputStream dis = getDataIn();
                    while (true) {
                        String command = dis.readUTF();
                        AutoPlugConsole.executeCommand(command);
                        AL.info("Executed Web-Command: " + command);
                    }
                } catch (Exception e) {
                    AL.warn(this.getClass(), e);
                }

            });
        thread.start();
        return true;
    }

    @Override
    public void close() throws IOException {
        try {
            if (thread != null && !thread.isInterrupted()) thread.interrupt();
        } catch (Exception e) {
            AL.warn("Failed to stop thread.", e);
        }
        thread = null;

        try {
            super.close();
        } catch (Exception e) {
            AL.warn("Failed to close connection.", e);
        }
    }
}

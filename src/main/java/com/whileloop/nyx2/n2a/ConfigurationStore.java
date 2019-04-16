/*
 * The MIT License
 *
 * Copyright 2019 Team whileLOOP.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.whileloop.nyx2.n2a;

import com.whileloop.nyx2.utils.NX2Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 *
 * @author sulochana
 */
public class ConfigurationStore extends NX2Logger {

    public enum ConfigKey {

        AUTH_TOKEN("AUTH_TOKEN");

        public String keyCode;

        private ConfigKey(String code) {
            this.keyCode = code;
        }
    }

    private static final ConfigurationStore instance = new ConfigurationStore();
    private final Properties prop = new Properties();
    private final String dirName = String.format("%s/.nyx2", System.getProperty("user.home"));
    private final String fileName = "config";
    private final String fileLocation = String.format("%s/%s", dirName, fileName);

    public ConfigurationStore() {
        creatConfigDirectory();
        loadFile();
    }

    private void loadFile() {
        try (InputStream inputStream = new FileInputStream(fileLocation)) {
            debug("Attempting to load configuration file");
            prop.load(inputStream);
            debug("Configuration file loaded");
        } catch (IOException ex) {
            debug("Failed to read configuration file: ", ex.getMessage());
        }
    }

    private void saveChanges() {
        try (OutputStream output = new FileOutputStream(fileLocation)) {
            prop.store(output, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            warn("Failed to save changes made to configurations", ex.getMessage());
        }
    }

    private void creatConfigDirectory() {
        File dir = new File(dirName);
        if (!dir.exists()) {
            try {
                debug("Creating directory: %s", dirName);
                dir.mkdirs();
                File configFile = new File("score.txt");
                configFile.createNewFile();
            } catch (IOException ex) {
                warn("Failed to create configuration directory", ex.getMessage());
            }
        }
    }

    public static String getConfiguration(ConfigKey key) {
        return instance.prop.getProperty(key.keyCode);
    }

    public static void setConfiguration(ConfigKey key, String value) {
        instance.debug("Setting %s to %s", key.toString(), value);
        instance.prop.setProperty(key.keyCode, value);
        instance.saveChanges();
    }

    public static void removeConfiguration(ConfigKey key) {
        instance.prop.remove(key.keyCode);
        instance.saveChanges();
    }

}

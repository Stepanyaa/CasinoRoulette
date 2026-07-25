package ru.stepanyaa.casinoRoulette.config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;

public final class ConfigService {

    private final File dataFolder;
    private final ResourceLoader resources;
    private final Logger logger;

    public interface ResourceLoader {
        InputStream open(String path);
    }

    public ConfigService(File dataFolder, ResourceLoader resources, Logger logger) {
        this.dataFolder = dataFolder;
        this.resources = resources;
        this.logger = logger;
    }

    public File file(String path) {
        return new File(dataFolder, path);
    }

    public File install(String path) {
        File target = file(path);
        String defaults = readResource(path);

        if (defaults == null) {

            if (!target.exists()) {
                logger.warning("No bundled default for " + path + "; starting from an empty file.");
                write(target, "");
            }
            return target;
        }

        if (!target.exists()) {
            write(target, defaults);
            logger.info("Created " + path);
            return target;
        }

        String current = read(target);
        YamlMerge.Result merged = YamlMerge.merge(current, defaults);
        if (merged.changed()) {

            backup(target);
            write(target, merged.text());

            List<String> added = merged.addedKeys();
            logger.info("Updated " + path + ": added " + added.size()
                    + " new option" + (added.size() == 1 ? "" : "s")
                    + " (" + preview(added) + "), comments and existing values kept.");
        }
        return target;
    }

    public void migrate(String oldPath, String newPath) {
        File from = file(oldPath);
        File to = file(newPath);
        if (!from.exists() || to.exists()) {
            return;
        }
        if (from.renameTo(to)) {
            logger.info("Migrated " + oldPath + " to " + newPath);
        } else {
            logger.warning("Could not migrate " + oldPath + " to " + newPath
                    + "; the old file was left untouched.");
        }
    }

    public void backup(File target) {
        try {
            File copy = new File(target.getParentFile(), target.getName() + ".backup");
            Files.copy(target.toPath(), copy.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            logger.warning("Could not back up " + target.getName() + ": " + failure.getMessage());
        }
    }

    public String read(File target) {
        try {
            byte[] bytes = Files.readAllBytes(target.toPath());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            logger.warning("Could not read " + target.getName() + ": " + failure.getMessage());
            return "";
        }
    }

    public void write(File target, String content) {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Could not create directory " + parent.getAbsolutePath());
            return;
        }
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8)) {
            writer.write(content);
        } catch (IOException failure) {
            logger.severe("Could not write " + target.getName() + ": " + failure.getMessage());
        }
    }

    public String readResource(String path) {

        InputStream in = resources.open(path.replace(File.separatorChar, '/'));
        if (in == null) {
            return null;
        }
        try (InputStream stream = in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            logger.warning("Could not read bundled " + path + ": " + failure.getMessage());
            return null;
        }
    }

    private static String preview(List<String> keys) {
        int shown = Math.min(keys.size(), 5);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(keys.get(i));
        }
        if (keys.size() > shown) {
            sb.append(", ...");
        }
        return sb.toString();
    }
}

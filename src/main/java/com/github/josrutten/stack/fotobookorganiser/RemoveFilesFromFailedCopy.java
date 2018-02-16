package com.github.josrutten.stack.fotobookorganiser;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class RemoveFilesFromFailedCopy {
    private final static Logger LOGGER = LoggerFactory.getLogger(FotoBookOrganiser.class);

    private Sardine sardine;

    private final String stackBaseUrl;
    private final String stackWebDavUrl;

    private int moved = 0;
    private int deleted = 0;
    private int fixed = 0;

    private RemoveFilesFromFailedCopy(String stackBaseUrl, String stackWebDavUrl) {
        this.stackBaseUrl = stackBaseUrl;
        this.stackWebDavUrl = stackWebDavUrl;
    }

    private void openWebDav(String username, String password) {
        sardine = SardineFactory.begin(username, password);
    }

    private void removeOrFixFiles(String oldDir, String newDir) throws IOException {
        removeOrFixFilesRecursive("", oldDir, newDir);

    }

    private void removeOrFixFilesRecursive(String folder, String oldDir, String newDir)  throws IOException {
        String folderToListUrl = stackWebDavUrl + oldDir + "/" + escapeSpaces(folder);
        LOGGER.info("listing folder {}", folderToListUrl);
        List<DavResource> resources = sardine.list(stackWebDavUrl + "/" + oldDir + "/" + escapeSpaces(folder), 1);
        LOGGER.info("found {} folders", resources.size());


        boolean firstItemIsParentDirSkipped = false;
        for (DavResource resource : resources) {
            if (resource.isDirectory()) {
                if (firstItemIsParentDirSkipped) {
                    String newFolder = folder + resource.getName() + "/";
                    removeOrFixFilesRecursive(newFolder, oldDir, newDir);
                } else {
                    firstItemIsParentDirSkipped = true;
                }
            } else if (!resource.getName().endsWith("JPG")) {
                removeOrFixFile(folder, resource, newDir);
            }
        }
    }

    private void removeOrFixFile(String folder, DavResource davResource, String newDir) throws IOException {
        String fileThatShouldExist = stackWebDavUrl + "/" + newDir + "/" + escapeSpaces(folder) + davResource.getName();
        LOGGER.info("checking if this file exists: {}", fileThatShouldExist);
        if (sardine.exists(fileThatShouldExist)) {
            // check if size is same
            davResource.getContentLength();
            DavResource newDavResource = sardine.list(stackWebDavUrl + "/" + newDir + "/" + escapeSpaces(folder) + "/" + davResource.getName(), 0).get(0);
            if (davResource.getContentLength().equals(newDavResource.getContentLength())) {
                // remove old file
                LOGGER.info("deleting: {}", davResource.getHref());
                sardine.delete(stackBaseUrl + davResource.getHref());
                deleted++;
            } else {
                LOGGER.info("override: {}", davResource.getHref());
                // override new file and move
                fixed++;
            }
        } else {
            LOGGER.info("moving : {} to {}", davResource.getHref(), fileThatShouldExist);
            // move
            sardine.move(stackBaseUrl + davResource.getHref(), fileThatShouldExist);
            moved++;
        }
    }

    public static void main(String ... args) throws IOException {
        Properties properties = new Properties();
        properties.load(FotoBookOrganiser.class.getResourceAsStream("/foto-organizer.properties"));
        properties.load(FotoBookOrganiser.class.getResourceAsStream("/foto-organizer-override.properties"));

        String username = properties.getProperty("username");
        String password = properties.getProperty("password");
        String stackBaseUrl = properties.getProperty("stackBaseUrl");
        String stackWebDavUrl = stackBaseUrl + "/" + properties.getProperty("webdavSubPath");

        RemoveFilesFromFailedCopy removeFilesFromFailedCopy = new RemoveFilesFromFailedCopy(stackBaseUrl, stackWebDavUrl);

        removeFilesFromFailedCopy.openWebDav(username, password);
        removeFilesFromFailedCopy.removeOrFixFiles("Images/petras%20telefoon%20sept%202017", "Images/petras%20telefoon");

        LOGGER.warn("DONE: deleted: {} moved: {} fixed: {}", removeFilesFromFailedCopy.deleted, removeFilesFromFailedCopy.moved, removeFilesFromFailedCopy.fixed);
        System.err.format("DONE: deleted: %d moved: %d fixed: %d", removeFilesFromFailedCopy.deleted, removeFilesFromFailedCopy.moved, removeFilesFromFailedCopy.fixed).println();
    }

    private static String escapeSpaces(String name) {
        return name.replaceAll(" ", "%20");
    }

}

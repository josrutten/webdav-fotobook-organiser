package com.github.josrutten.stack.fotobookorganiser;

import com.drew.imaging.ImageProcessingException;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FotoBookOrganiser {
    private final static Logger LOGGER = LoggerFactory.getLogger(FotoBookOrganiser.class);

    private final Sardine sardine;

    private final String stackBaseUrl;
    private final String stackWebDavUrl;

    private final MetaDataTools metaDataTools;

    public FotoBookOrganiser(Properties properties, Sardine sardine, MetaDataTools metaDataTools) {
        stackBaseUrl = properties.getProperty("stackBaseUrl");
        stackWebDavUrl = stackBaseUrl + "/" + properties.getProperty("webdavSubPath");
        this.sardine = sardine;
        this.metaDataTools = metaDataTools;
    }


    public static void main(String ... args) {
        try {
            Properties properties = new Properties();
            properties.load(FotoBookOrganiser.class.getResourceAsStream("/foto-organizer.properties"));
            properties.load(FotoBookOrganiser.class.getResourceAsStream("/foto-organizer-override.properties"));

            String username = properties.getProperty("username");
            String password = properties.getProperty("password");

            FotoBookOrganiser fotoBookOrganiser = new FotoBookOrganiser(properties, openWebDav(username,password), new MetaDataTools());

            fotoBookOrganiser.reorganiseFolder("Images/sony%20camera");
        } catch (Exception e) {
            LOGGER.error("Something went wrong while reorganizing", e);
        }
    }

    private static Sardine openWebDav(String username, String password) {
        return SardineFactory.begin(username, password);
    }

    public void reorganiseFolder(String folderPath) throws IOException, ImageProcessingException {
        reorganiseFolder(folderPath, 0);
    }

    public void reorganiseFolder(String folderPath, int maxFiles) throws IOException, ImageProcessingException {
        LOGGER.info("Reorganising folder: {}", folderPath);
        List<DavResource> resources = sardine.list(stackWebDavUrl + '/' + folderPath);
        int counter = 0;
        for (DavResource resource : resources) {
            String url = stackBaseUrl + resource.getHref();
            LOGGER.info("getting info on file number {} : {}", ++counter,  url);
            if (maxFiles > 0 && maxFiles >= counter) {
                break;
            }
            if (!resource.isDirectory()) {
                try (InputStream inputStream = sardine.get(url) ){
                    LocalDate takenAt = metaDataTools.getTakenAt(resource.getContentType(), inputStream).toLocalDate();
                    moveDavResourceForDate(takenAt, resource);
                } catch (Exception e) {
                    // dirty: just continue
                    LOGGER.error("Failed to move file: " + resource.getHref(), e);
                }
            }
        }
    }

    private void moveDavResourceForDate(LocalDate date, DavResource file) throws IOException {
        File parentAsFile = new File(file.getHref().toString()).getParentFile();

        String monthSubFolder = date.format(DateTimeFormatter.ofPattern("MMMM%20yyyy", new Locale("nl", "NL")));
        String daySubFolder = date.format(DateTimeFormatter.ISO_DATE);
        String yearSubFolder = date.format(DateTimeFormatter.ofPattern("yyyy"));
        String yearFullPath = parentAsFile.getCanonicalPath() + "/" + yearSubFolder;
        String monthFullPath = yearFullPath +  "/" + monthSubFolder;
        String dayFullPath = monthFullPath + "/" + daySubFolder;

        if (!sardine.exists(stackBaseUrl +  yearFullPath)) {
            sardine.createDirectory(stackBaseUrl +  yearFullPath);
        }
        if (!sardine.exists(stackBaseUrl +  monthFullPath)) {
            sardine.createDirectory(stackBaseUrl +  monthFullPath);
        }
        if (!sardine.exists(stackBaseUrl +  dayFullPath)) {
            sardine.createDirectory(stackBaseUrl +  dayFullPath);
        }
        moveDavResourceToNewFolder(file, dayFullPath);
    }

    private void moveDavResourceToNewFolder(DavResource file, String newFolder) throws IOException {
        String oldUrl = stackBaseUrl + file.getHref();
        String newUrl = stackBaseUrl + newFolder + '/' + new File(file.getHref().toString()).getName();

        try {
            sardine.move(oldUrl, newUrl);
            LOGGER.info("moved from {} to {}", oldUrl, newUrl);
        } catch (IOException ioe) {
            LOGGER.error("move failed for file" +  file.getName() + " - SKIPPING", ioe);
        }
    }

}

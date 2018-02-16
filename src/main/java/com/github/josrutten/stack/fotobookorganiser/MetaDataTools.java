package com.github.josrutten.stack.fotobookorganiser;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.thebuzzmedia.exiftool.ExifTool;
import com.thebuzzmedia.exiftool.ExifToolBuilder;
import com.thebuzzmedia.exiftool.core.StandardTag;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MetaDataTools {

    public static void main(String... args) throws Exception {
        MetaDataTools metaDataTools = new MetaDataTools();

        metaDataTools.printImageProperties("/home/jos/Downloads/IMAG0001.jpg");
        metaDataTools.printImageProperties("/home/jos/Downloads/DSC05297.JPG");
        metaDataTools.printImageProperties("/home/jos/Downloads/IMAG0002.jpg");
        metaDataTools.printVideoPropertiesUsingTmpFile("/home/jos/Downloads/VIDEO0003.3gp");
        metaDataTools.printVideoPropertiesUsingTmpFile("/home/jos/Downloads/VIDEO0007.mp4");
    }

    private void printVideoProperties(String fileName) throws IOException {
        System.out.println("dateTimeTaken : " + getVideoDateTimeOriginalProperty(fileName));
    }

    private void printVideoPropertiesUsingTmpFile(String fileName) throws IOException {
        LocalDateTime takenAt = getVideoTakenAt(new FileInputStream(fileName));
        System.out.println("dateTimeTaken (via tmp file) : " + takenAt);
    }

    private void printImageProperties(String filename) throws IOException, ImageProcessingException {
        System.out.println("file : " + filename);
        Metadata metadata = ImageMetadataReader.readMetadata(new File(filename));
        for (Directory directory : metadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                if (tag.hasTagName() && tag.getTagName().contains("Date/Time")) {
                    System.out.println("\t" + tag + " - dirType: " + directory.getClass().getSimpleName());
                }
            }
        }
    }

    private String getVideoDateTimeOriginalProperty(String filename) throws IOException {
        ExifTool exifTool = new ExifToolBuilder().build();
        Map<com.thebuzzmedia.exiftool.Tag, String> imageMeta = exifTool.getImageMeta(new File(filename), Arrays.asList(StandardTag.DATE_TIME_ORIGINAL, StandardTag.CREATE_DATE));


        String dateTimeOriginal = imageMeta.get(StandardTag.DATE_TIME_ORIGINAL);
        if (dateTimeOriginal != null) {
            return dateTimeOriginal;
        }
        return imageMeta.get(StandardTag.CREATE_DATE);
    }

    private LocalDateTime getVideoTakenAt(InputStream inputStream) throws IOException {
        File file = File.createTempFile("video", "tmp");
        try {
            IOUtils.copy(inputStream, new FileOutputStream(file));

            String imageTakenAtStr = getVideoDateTimeOriginalProperty(file.getCanonicalPath());
            return LocalDateTime.parse(imageTakenAtStr, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss[XXX][X]"));
        } finally {
            file.delete();
        }
    }

    private LocalDateTime getImageTakenAt(InputStream inputStream) throws ImageProcessingException, IOException {
        Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

        Collection<ExifSubIFDDirectory> directories = metadata.getDirectoriesOfType(ExifSubIFDDirectory.class);
        Date date = new Date(System.currentTimeMillis());
        if (directories != null && !directories.isEmpty()) {
            date = directories.stream().map(ExifSubIFDDirectory::getDateOriginal).findFirst().get();
        }

        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public LocalDateTime getTakenAt(String contentType, InputStream inputStream) throws ImageProcessingException, IOException {
        if (contentType.startsWith("image/")) {
            return getImageTakenAt(inputStream);
        } else {
            return getVideoTakenAt(inputStream);
        }
    }
}

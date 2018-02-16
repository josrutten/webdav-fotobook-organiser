package com.github.josrutten.stack.fotobookorganiser;


import com.drew.imaging.ImageProcessingException;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FotoBookOrganiserTest {

    private static final String STACK_BASE = "http://stack.com/dummy";
    private static final String WEBDAV_PATH = "dav";

    @Mock
    private Sardine sardine;

    @Mock
    private MetaDataTools metaDataTools;

    private FotoBookOrganiser fotoBookOrganiser;

    @Before
    public void setUpSystemUnderTest() {
        Properties properties = new Properties();
        properties.setProperty("stackBaseUrl", STACK_BASE);
        properties.setProperty("webdavSubPath", WEBDAV_PATH);
        fotoBookOrganiser = new FotoBookOrganiser(properties, sardine, metaDataTools);
    }

    @Before
    public void setUpDirectoryExistsMethod() throws IOException {
        final List<String> alreadyFoundList = new ArrayList<>();
        doAnswer(invocation -> {
            String path = invocation.getArgumentAt(0, String.class);
            boolean exists = alreadyFoundList.contains(path);
            alreadyFoundList.add(path);
            return exists;
        }).when(sardine).exists(anyString());
    }

    @Test
    public void itShouldReorganize() throws IOException, URISyntaxException, ImageProcessingException {
        // Given
        List<DavResource> davResources = new ArrayList<>();
        String folder = "folderToOrganize";
        davResources.add(handleMocksForFile(folder, "image001.jpg", "image/jpg", false, LocalDateTime.of(2015, 9, 26, 12,0)));
        davResources.add(handleMocksForFile(folder, "image002.jpg", "image/jpg", false, LocalDateTime.of(2018, 2, 16, 16,30)));
        davResources.add(handleMocksForFile(folder, "image003.jpg", "image/jpg", false, LocalDateTime.of(2018, 2, 12, 9,0)));
        davResources.add(handleMocksForFile(folder, "image004.jpg", "image/jpg", false, LocalDateTime.of(2018, 2, 16, 17,27)));
        davResources.add(handleMocksForFile(folder, "subfolder", null, true, null));
        when(sardine.list("http://stack.com/dummy/dav/" + folder)).thenReturn(davResources);

        // When
        fotoBookOrganiser.reorganiseFolder(folder);

        // Then
        verify(sardine, times(7)).createDirectory(anyString());
        verify(sardine).createDirectory(STACK_BASE + "/" + WEBDAV_PATH + "/" + folder + "/2015/september%202015");
        verify(sardine).createDirectory(STACK_BASE + "/" + WEBDAV_PATH + "/" + folder + "/2018/februari%202018");
        verify(sardine).createDirectory(STACK_BASE + "/" + WEBDAV_PATH + "/" + folder + "/2018/februari%202018/2018-02-12");
        verify(sardine).createDirectory(STACK_BASE + "/" + WEBDAV_PATH + "/" + folder + "/2018/februari%202018/2018-02-16");
        verify(sardine).createDirectory(STACK_BASE + "/" + WEBDAV_PATH + "/" + folder + "/2015/september%202015/2015-09-26");
        verify(sardine).createDirectory(STACK_BASE + "/" + WEBDAV_PATH + "/" + folder + "/2018");
        verify(sardine).createDirectory(STACK_BASE + "/" + WEBDAV_PATH + "/" + folder + "/2015");
        verify(sardine, times(4)).move(anyString(), anyString());
        verify(sardine).move("http://stack.com/dummy/dav/folderToOrganize/image001.jpg",
                "http://stack.com/dummy/dav/folderToOrganize/2015/september%202015/2015-09-26/image001.jpg");
        verify(sardine).move("http://stack.com/dummy/dav/folderToOrganize/image002.jpg",
                "http://stack.com/dummy/dav/folderToOrganize/2018/februari%202018/2018-02-16/image002.jpg");
        verify(sardine).move("http://stack.com/dummy/dav/folderToOrganize/image003.jpg",
                "http://stack.com/dummy/dav/folderToOrganize/2018/februari%202018/2018-02-12/image003.jpg");
        verify(sardine).move("http://stack.com/dummy/dav/folderToOrganize/image004.jpg",
                "http://stack.com/dummy/dav/folderToOrganize/2018/februari%202018/2018-02-16/image004.jpg");
    }

    private DavResource handleMocksForFile(String path, String fileName, String contentType, boolean isDirectory, LocalDateTime localDateTime) throws URISyntaxException, IOException, ImageProcessingException {
        DavResource davResource = mock(DavResource.class);
        String uri = "/"+ WEBDAV_PATH  + "/" + path + "/" + fileName;
        when(davResource.getHref()).thenReturn(new URI(uri));
        when(davResource.isDirectory()).thenReturn(isDirectory);
        when(davResource.getContentType()).thenReturn(contentType);
        if (!isDirectory) {
            InputStream inputStream = mock(InputStream.class);
            when(sardine.get(STACK_BASE + uri)).thenReturn(inputStream);
            when(metaDataTools.getTakenAt(contentType, inputStream)).thenReturn(localDateTime);
        }
        return davResource;
    }
}

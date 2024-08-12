package com.dobest1.boyka;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.intellij.openapi.project.Project;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BoykaAIFileToolsTest {

    @Mock
    private Project project;

    private BoykaAIFileTools fileTools;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(project.getBasePath()).thenReturn(System.getProperty("java.io.tmpdir"));
        fileTools = new BoykaAIFileTools(project);
    }

    @Test
    public void testCreateDirectory() {
        assertTrue(fileTools.createDirectory("testDir"));
        assertFalse(fileTools.createDirectory("../invalidDir"));
    }

    @Test
    public void testCreateFile() {
        assertTrue(fileTools.createFile("testFile.txt"));
        assertFalse(fileTools.createFile("../invalidFile.txt"));
    }

    // 添加更多测试...
}
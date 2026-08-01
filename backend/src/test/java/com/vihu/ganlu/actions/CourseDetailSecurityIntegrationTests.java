package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.CourseDetailService;
import com.vihu.ganlu.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseDetailSecurityIntegrationTests {
    @TempDir
    Path tempDirectory;

    private CourseDetailService materialService;
    private TokenService tokenService;
    private MockMvc mockMvc;
    private final Map<Integer, UserEntity> users = new HashMap<>();

    @BeforeEach
    void setUp() {
        materialService = mock(CourseDetailService.class);
        UserService userService = mock(UserService.class);
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "test-token-secret-with-more-than-32-bytes");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);
        users.put(1, user(1, 0));
        users.put(2, user(2, 1));
        users.put(3, user(3, 2));
        when(userService.findUserById(anyInt())).thenAnswer(invocation -> users.get(invocation.getArgument(0)));

        CourseDetailAction action = new CourseDetailAction(materialService);
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(action)
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    void visitorCanReadMetadataButCannotReadPreviewOrDownloadBytes() throws Exception {
        CourseDetailEntity material = material(8);
        when(materialService.getCourseById(8)).thenReturn(material);

        mockMvc.perform(get("/courseDetail/materials/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.id").value(8))
                .andExpect(jsonPath("$.content.previewFilePath").doesNotExist())
                .andExpect(jsonPath("$.content.originalFilePath").doesNotExist());
        mockMvc.perform(get("/courseDetail/materials/8/preview"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/courseDetail/materials/8/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCanPreviewAndDownloadButCannotUploadOrDelete() throws Exception {
        CourseDetailEntity material = material(9);
        Path file = tempDirectory.resolve("lesson.pdf");
        Files.write(file, "%PDF-1.7\nlesson".getBytes(StandardCharsets.US_ASCII));
        when(materialService.getCourseById(9)).thenReturn(material);
        when(materialService.getPreviewPath(9)).thenReturn(file);
        when(materialService.getDownloadPath(9)).thenReturn(file);
        String authorization = bearer(users.get(3));

        mockMvc.perform(get("/courseDetail/materials/9/preview").header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(get("/courseDetail/materials/9/download").header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/courseDetail/materials/9").header("Authorization", authorization))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/courseDetail/materials")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void teamAccountCanCreateDeleteAndCancelUploads() throws Exception {
        CourseDetailEntity created = material(10);
        when(materialService.createMaterial(any(), any())).thenReturn(created);
        when(materialService.deleteCourseById(10)).thenReturn(true);
        String authorization = bearer(users.get(2));

        mockMvc.perform(post("/courseDetail/materials")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/courseDetail/materials/10").header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/courseDetail/uploadSession")
                        .header("Authorization", authorization)
                        .param("purpose", "MATERIAL")
                        .param("identifier", "900150983cd24fb0d6963f7d28e17f72"))
                .andExpect(status().isOk());

        verify(materialService).cancelUpload(
                "900150983cd24fb0d6963f7d28e17f72", "MATERIAL", null, 2);
    }

    @Test
    void administratorCanUploadAChunkThroughRealMvcBinding() throws Exception {
        when(materialService.saveChunk(any(), anyInt(), anyInt(), any(), any(),
                org.mockito.ArgumentMatchers.anyLong(), any(), anyInt())).thenReturn("1");
        byte[] content = "%PDF-1.7\nlesson".getBytes(StandardCharsets.US_ASCII);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/courseDetail/uploadChunk")
                        .file("file", content)
                        .header("Authorization", bearer(users.get(1)))
                        .param("chunkNumber", "1")
                        .param("totalChunks", "1")
                        .param("identifier", "bcb6f5295a6a99893c678c8f52e2d30d")
                        .param("filename", "lesson.pdf")
                        .param("expectedSize", String.valueOf(content.length))
                        .param("purpose", "MATERIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String bearer(UserEntity user) {
        return "Bearer " + tokenService.createToken(user);
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        user.setUsername("user" + id);
        return user;
    }

    private CourseDetailEntity material(int id) {
        CourseDetailEntity material = new CourseDetailEntity();
        material.setId(id);
        material.setTitle("测试课件");
        material.setPreviewStatus("READY");
        material.setMimeType("application/pdf");
        material.setOriginalFilename("lesson.pdf");
        material.setPreviewFilePath("protected/material-previews/secret.pdf");
        material.setOriginalFilePath("protected/materials/secret.pdf");
        return material;
    }
}

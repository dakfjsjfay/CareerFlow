package com.team.resume.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;
import com.team.resume.resume.service.ResumeVersionService;
import com.team.resume.resume.vo.ResumeContent;

@ExtendWith(MockitoExtension.class)
class ResumeVersionServiceTest {

    @Mock
    private ResumeMapper resumeMapper;

    @Mock
    private ResumeVersionMapper versionMapper;

    private ResumeVersionService service;

    @BeforeEach
    void setUp() {
        service = new ResumeVersionService(resumeMapper, versionMapper, new ObjectMapper());
    }

    @Test
    void createsNextVersionAndUpdatesCurrent() {
        when(resumeMapper.selectByIdForUpdate(1L)).thenReturn(resume(1L, 7L));
        when(versionMapper.selectMaxVersionNumber(1L)).thenReturn(2);
        when(versionMapper.insert(any(ResumeVersion.class))).thenAnswer(invocation -> {
            ResumeVersion version = invocation.getArgument(0);
            version.setId(99L);
            return 1;
        });

        ResumeVersion version = service.create(7L, 1L, contentWithTemplate("tpl-1"), "INITIAL");

        assertEquals(3, version.getVersionNumber());
        assertEquals(99L, version.getId());
        assertEquals("tpl-1", version.getContent().path("meta").path("templateId").asText());
        verify(resumeMapper).updateById(argThat((Resume resume) ->
                resume.getCurrentVersionId() != null && resume.getCurrentVersionId() == 99L));
    }

    @Test
    void rejectsOtherUsersResume() {
        when(resumeMapper.selectByIdForUpdate(1L)).thenReturn(resume(1L, 8L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(7L, 1L, contentWithTemplate("tpl-1"), "INITIAL"));

        assertEquals(403, error.getCode());
        verify(versionMapper, never()).insert(any(ResumeVersion.class));
    }

    @Test
    void returns404WhenResumeMissing() {
        when(resumeMapper.selectByIdForUpdate(1L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(7L, 1L, contentWithTemplate("tpl-1"), "INITIAL"));

        assertEquals(404, error.getCode());
    }

    @Test
    void mapsDuplicateVersionNumberTo409() {
        when(resumeMapper.selectByIdForUpdate(1L)).thenReturn(resume(1L, 7L));
        when(versionMapper.selectMaxVersionNumber(1L)).thenReturn(2);
        when(versionMapper.insert(any(ResumeVersion.class))).thenThrow(new DuplicateKeyException("uk_resume_version"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(7L, 1L, contentWithTemplate("tpl-1"), "INITIAL"));

        assertEquals(409, error.getCode());
    }

    @Test
    void listRejectsOtherUsersResume() {
        when(resumeMapper.selectById(1L)).thenReturn(resume(1L, 8L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.list(7L, 1L));

        assertEquals(403, error.getCode());
    }

    @Test
    void getRejectsVersionFromAnotherResume() {
        when(resumeMapper.selectById(1L)).thenReturn(resume(1L, 7L));
        ResumeVersion other = new ResumeVersion();
        other.setId(5L);
        other.setResumeId(2L);
        when(versionMapper.selectById(5L)).thenReturn(other);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.get(7L, 1L, 5L));

        assertEquals(404, error.getCode());
    }

    private static Resume resume(Long id, Long userId) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setUserId(userId);
        resume.setResumeName("我的简历");
        return resume;
    }

    private static ResumeContent contentWithTemplate(String templateId) {
        return new ResumeContent(
                new ResumeContent.Meta(templateId, "模板一", "2026-11-10T12:00:00", "AI",
                        "INITIAL", null, new ArrayList<>()),
                new ResumeContent.PersonalInfo("张同学", null, null, null, null,
                        "深圳大学", "软件工程", "本科", 2028, 0, null),
                new ResumeContent.JobIntention("Java 开发实习生", "深圳", "面议"),
                List.of(), List.of("Java"), List.of(), List.of(), "");
    }
}

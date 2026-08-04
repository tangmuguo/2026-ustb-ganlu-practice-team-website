package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.BannerEntity;
import com.vihu.ganlu.entitys.NewsEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.BannerMapper;
import com.vihu.ganlu.mappers.NewsMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.impl.BannerServiceImpl;
import com.vihu.ganlu.service.impl.NewsServiceImpl;
import com.vihu.ganlu.service.impl.PublicImageLifecycleService;
import com.vihu.ganlu.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.InputStream;
import java.util.Scanner;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicImageReplacementLockingTests {

    @Test
    void mapperContractsUseDatabaseRowLocks() {
        assertForUpdate("mapper/BannerMapper.xml", "findByIdForUpdate");
        assertForUpdate("mapper/NewsMapper.xml", "findByIdForUpdate");
        assertForUpdate("mapper/UserMapper.xml", "findUserByIdForUpdate");
        assertForUpdate("mapper/TeamPageWordMapper.xml", "findByIdForUpdate");
    }

    @Test
    void bannerReplacementLocksBeforeChangingImageLifecycle() {
        BannerMapper mapper = mock(BannerMapper.class);
        PublicImageLifecycleService lifecycle = mock(PublicImageLifecycleService.class);
        BannerEntity existing = banner(4, "images/1/old.jpg");
        BannerEntity update = banner(4, null);
        update.setImageUploadUserId(1);
        update.setImageUploadToken("token");
        when(mapper.findByIdForUpdate(4)).thenReturn(existing);
        when(lifecycle.promote(1, "token")).thenReturn("images/1/new.jpg");
        when(mapper.update(update)).thenReturn(1);

        assertEquals(1, new BannerServiceImpl(mapper, lifecycle).updateBanner(update));

        InOrder order = inOrder(mapper, lifecycle);
        order.verify(mapper).findByIdForUpdate(4);
        order.verify(lifecycle).deletePublicImageAfterCommit("images/1/old.jpg");
        order.verify(lifecycle).promote(1, "token");
        order.verify(mapper).update(update);
    }

    @Test
    void bannerTextOnlyUpdateKeepsLockedLatestImage() {
        BannerMapper mapper = mock(BannerMapper.class);
        PublicImageLifecycleService lifecycle = mock(PublicImageLifecycleService.class);
        BannerEntity existing = banner(5, "images/2/latest.jpg");
        BannerEntity update = banner(5, null);
        when(mapper.findByIdForUpdate(5)).thenReturn(existing);
        when(mapper.update(any(BannerEntity.class))).thenReturn(1);

        assertEquals(1, new BannerServiceImpl(mapper, lifecycle).updateBanner(update));

        ArgumentCaptor<BannerEntity> saved = ArgumentCaptor.forClass(BannerEntity.class);
        verify(mapper).update(saved.capture());
        assertEquals("images/2/latest.jpg", saved.getValue().getImageUrl());
        verify(lifecycle).requireManagedImageAsset("images/2/latest.jpg");
    }

    @Test
    void newsReplacementUsesLockedRow() {
        NewsMapper mapper = mock(NewsMapper.class);
        PublicImageLifecycleService lifecycle = mock(PublicImageLifecycleService.class);
        NewsEntity existing = new NewsEntity();
        existing.setId(6);
        existing.setImageUrl("images/3/old.jpg");
        NewsEntity update = new NewsEntity();
        update.setId(6);
        update.setImageUploadUserId(3);
        update.setImageUploadToken("token");
        when(mapper.findByIdForUpdate(6)).thenReturn(existing);
        when(lifecycle.promote(3, "token")).thenReturn("images/3/new.jpg");
        when(mapper.update(update)).thenReturn(1);

        assertEquals(1, new NewsServiceImpl(mapper, lifecycle).updateNews(update));
        verify(mapper).findByIdForUpdate(6);
        verify(mapper, never()).findById(6);
    }

    @Test
    void userReplacementUsesLockedRow() {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PublicImageLifecycleService lifecycle = mock(PublicImageLifecycleService.class);
        UserEntity existing = new UserEntity();
        existing.setId(7);
        existing.setImageUrl("images/4/old.jpg");
        UserEntity update = new UserEntity();
        update.setId(7);
        update.setUsername("valid-user");
        update.setPassword("");
        update.setImageUploadUserId(4);
        update.setImageUploadToken("token");
        when(mapper.findUserByIdForUpdate(7)).thenReturn(existing);
        when(lifecycle.promote(4, "token")).thenReturn("images/4/new.jpg");
        when(mapper.updateUserById(update)).thenReturn(1);

        assertEquals(1, new UserServiceImpl(mapper, encoder, lifecycle).updateUserById(update));
        verify(mapper).findUserByIdForUpdate(7);
        verify(mapper, never()).findUserById(7);
    }

    @Test
    void bannerDeletionLocksAndUsesLatestImagePath() {
        BannerMapper mapper = mock(BannerMapper.class);
        PublicImageLifecycleService lifecycle = mock(PublicImageLifecycleService.class);
        BannerEntity latest = banner(8, "images/8/latest.jpg");
        when(mapper.findByIdForUpdate(8)).thenReturn(latest);
        when(mapper.delete(8)).thenReturn(1);

        assertEquals(1, new BannerServiceImpl(mapper, lifecycle).deleteBanner(8));

        InOrder order = inOrder(mapper, lifecycle);
        order.verify(mapper).findByIdForUpdate(8);
        order.verify(mapper).delete(8);
        order.verify(lifecycle).deletePublicImageAfterCommit("images/8/latest.jpg");
        verify(mapper, never()).findById(8);
    }

    @Test
    void newsDeletionUsesSameRowLockAsReplacement() {
        NewsMapper mapper = mock(NewsMapper.class);
        PublicImageLifecycleService lifecycle = mock(PublicImageLifecycleService.class);
        NewsEntity latest = new NewsEntity();
        latest.setId(9);
        latest.setImageUrl("images/9/latest.jpg");
        when(mapper.findByIdForUpdate(9)).thenReturn(latest);
        when(mapper.delete(9)).thenReturn(1);

        assertEquals(1, new NewsServiceImpl(mapper, lifecycle).deleteNews(9));
        verify(mapper).findByIdForUpdate(9);
        verify(mapper, never()).findById(9);
        verify(lifecycle).deletePublicImageAfterCommit("images/9/latest.jpg");
    }

    @Test
    void userBatchDeletionLocksDistinctIdsInStableOrder() {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PublicImageLifecycleService lifecycle = mock(PublicImageLifecycleService.class);
        UserEntity two = new UserEntity();
        two.setId(2);
        UserEntity five = new UserEntity();
        five.setId(5);
        when(mapper.findUserByIdForUpdate(2)).thenReturn(two);
        when(mapper.findUserByIdForUpdate(5)).thenReturn(five);
        when(mapper.deleteUserByIds(Arrays.asList(2, 5))).thenReturn(2);

        assertEquals(2, new UserServiceImpl(mapper, encoder, lifecycle)
                .deleteUserByIds(Arrays.asList(5, 2, 5)));

        InOrder order = inOrder(mapper);
        order.verify(mapper).findUserByIdForUpdate(2);
        order.verify(mapper).findUserByIdForUpdate(5);
        order.verify(mapper).countTeamBindingsByUserIds(Arrays.asList(2, 5));
        order.verify(mapper).deleteUserByIds(Arrays.asList(2, 5));
        verify(mapper, never()).findUserById(anyInt());
    }

    @Test
    void makingLegacyBannerVisibleIsBlockedUntilItsAssetIsMigrated() {
        BannerMapper mapper = mock(BannerMapper.class);
        PublicImageLifecycleService lifecycle = mock(PublicImageLifecycleService.class);
        BannerEntity existing = banner(12, "legacy/shared-banner.jpg");
        when(mapper.findByIdForUpdate(12)).thenReturn(existing);
        doThrow(new IllegalStateException("公共图片迁移未完成"))
                .when(lifecycle).requireManagedImageAsset(existing.getImageUrl());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new BannerServiceImpl(mapper, lifecycle).updateBannerStatus(12, 1));

        verify(mapper, never()).updateStatus(anyInt(), anyInt());
    }

    private BannerEntity banner(int id, String imageUrl) {
        BannerEntity banner = new BannerEntity();
        banner.setId(id);
        banner.setImageUrl(imageUrl);
        return banner;
    }

    private void assertForUpdate(String resource, String statementId) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(resource);
        assertTrue(input != null, "找不到 mapper: " + resource);
        try (Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            String xml = scanner.hasNext() ? scanner.next().replaceAll("\\s+", " ").toLowerCase() : "";
            assertTrue(xml.contains("id=\"" + statementId.toLowerCase() + "\""));
            int statement = xml.indexOf("id=\"" + statementId.toLowerCase() + "\"");
            int end = xml.indexOf("</select>", statement);
            assertTrue(statement >= 0 && end > statement
                    && xml.substring(statement, end).contains("for update"), resource);
        }
    }
}

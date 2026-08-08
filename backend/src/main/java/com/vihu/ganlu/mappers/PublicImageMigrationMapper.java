package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.PublicImageReferenceEntity;

import java.util.List;

public interface PublicImageMigrationMapper {
    List<PublicImageReferenceEntity> findBusinessReferences();

    List<PublicImageReferenceEntity> findCourseCoverReferences();

    List<PublicImageReferenceEntity> findMaterialFileReferences();
}

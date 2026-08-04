package com.vihu.ganlu.entitys;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PublicImageMigrationReport {
    private boolean migrationAllowed;
    private boolean consistent;
    private int referenceCount;
    private int managedReferenceCount;
    private int externalReferenceCount;
    private int registeredAssetCount;
    private int diskFileCount;
    private long verifiedReferenceBytes;
    private long registeredAssetBytes;
    private long diskBytes;
    private int candidateCount;
    private int repairCount;
    private int migratedCount;
    private int repairedCount;
    private List<PublicImageMigrationIssue> issues = new ArrayList<>();
}

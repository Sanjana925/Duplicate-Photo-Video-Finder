package com.sanjana.duplicatefinder.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "media",
        indices = {
                @Index(value = {"size"}),
                @Index(value = {"quickFingerprint"}),
                @Index(value = {"sha256"}),
                @Index(value = {"mediaType"})
        }
)
public class MediaEntity {

    @PrimaryKey
    @NonNull
    public String uri = "";

    public String name = "";
    public String mimeType = "";
    public long size = 0L;
    public long dateAdded = 0L;
    public long dateModified = 0L;
    public int width = 0;
    public int height = 0;
    public long duration = 0L;
    public String relativePath = "";
    public String mediaType = "";
    public String quickFingerprint = "";
    public String sha256 = "";

    // Phase 4.2:
    // Compact visual fingerprint generated from sampled video frames.
    public String videoFingerprint = "";

    public long scannedAt = 0L;

    public MediaEntity() {
    }

    public MediaEntity(
            @NonNull String uri,
            String name,
            String mimeType,
            long size,
            long dateAdded,
            long dateModified,
            int width,
            int height,
            long duration,
            String relativePath,
            String mediaType,
            long scannedAt
    ) {
        this.uri = uri;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
        this.dateAdded = dateAdded;
        this.dateModified = dateModified;
        this.width = width;
        this.height = height;
        this.duration = duration;
        this.relativePath = relativePath;
        this.mediaType = mediaType;
        this.scannedAt = scannedAt;
    }
}
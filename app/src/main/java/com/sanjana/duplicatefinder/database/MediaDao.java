package com.sanjana.duplicatefinder.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MediaEntity> media);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MediaEntity media);

    @Query("DELETE FROM media")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM media")
    int getTotalCount();

    @Query(
            "SELECT COUNT(*) FROM media " +
                    "WHERE mediaType = 'PHOTO'"
    )
    int getPhotoCount();

    @Query(
            "SELECT COUNT(*) FROM media " +
                    "WHERE mediaType = 'VIDEO'"
    )
    int getVideoCount();

    /*
     * Return only file sizes that occur more than once.
     *
     * We do NOT load every media item into memory.
     */
    @Query(
            "SELECT size FROM media " +
                    "WHERE size > 0 " +
                    "GROUP BY size " +
                    "HAVING COUNT(*) > 1 " +
                    "ORDER BY size"
    )
    List<Long> getDuplicateCandidateSizes();

    /*
     * Return only media having one particular size.
     */
    @Query(
            "SELECT * FROM media " +
                    "WHERE size = :size " +
                    "ORDER BY uri"
    )
    List<MediaEntity> getMediaWithSize(long size);

    /*
     * Find all exact duplicate files.
     *
     * A SHA-256 that occurs at least twice is an exact
     * duplicate group.
     */
    @Query(
            "SELECT * FROM media " +
                    "WHERE sha256 IS NOT NULL " +
                    "AND sha256 != '' " +
                    "AND sha256 IN (" +
                    "   SELECT sha256 FROM media " +
                    "   WHERE sha256 IS NOT NULL " +
                    "   AND sha256 != '' " +
                    "   GROUP BY sha256 " +
                    "   HAVING COUNT(*) > 1" +
                    ") " +
                    "ORDER BY mediaType, sha256, relativePath, name"
    )
    List<MediaEntity> getExactDuplicateItems();

    @Query(
            "SELECT COUNT(*) FROM (" +
                    "   SELECT sha256 FROM media " +
                    "   WHERE mediaType = :mediaType " +
                    "   AND sha256 IS NOT NULL " +
                    "   AND sha256 != '' " +
                    "   GROUP BY sha256 " +
                    "   HAVING COUNT(*) > 1" +
                    ")"
    )
    int getDuplicateGroupCount(
            String mediaType
    );

    @Query(
            "SELECT COUNT(*) FROM media " +
                    "WHERE mediaType = :mediaType " +
                    "AND sha256 IS NOT NULL " +
                    "AND sha256 != '' " +
                    "AND sha256 IN (" +
                    "   SELECT sha256 FROM media " +
                    "   WHERE mediaType = :mediaType " +
                    "   AND sha256 IS NOT NULL " +
                    "   AND sha256 != '' " +
                    "   GROUP BY sha256 " +
                    "   HAVING COUNT(*) > 1" +
                    ")"
    )
    int getDuplicateItemCount(
            String mediaType
    );

    @Query(
            "DELETE FROM media " +
                    "WHERE uri IN (:uris)"
    )
    void deleteByUris(
            List<String> uris
    );

    @Query(
            "UPDATE media " +
                    "SET quickFingerprint = :fingerprint " +
                    "WHERE uri = :uri"
    )
    void updateQuickFingerprint(
            String uri,
            String fingerprint
    );

    @Query(
            "UPDATE media " +
                    "SET sha256 = :sha256 " +
                    "WHERE uri = :uri"
    )
    void updateSha256(
            String uri,
            String sha256
    );

    @Query(
            "DELETE FROM media " +
                    "WHERE uri = :uri"
    )
    void deleteByUri(
            String uri
    );
}
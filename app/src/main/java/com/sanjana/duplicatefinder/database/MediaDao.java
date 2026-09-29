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

    @Query(
            "SELECT size FROM media " +
                    "WHERE size > 0 " +
                    "GROUP BY size " +
                    "HAVING COUNT(*) > 1 " +
                    "ORDER BY size"
    )
    List<Long> getDuplicateCandidateSizes();

    @Query(
            "SELECT * FROM media " +
                    "WHERE size = :size " +
                    "ORDER BY mediaType, relativePath, name, uri"
    )
    List<MediaEntity> getMediaWithSize(long size);

    @Query(
            "SELECT * FROM media " +
                    "WHERE sha256 IS NOT NULL " +
                    "AND sha256 != '' " +
                    "AND (mediaType, sha256) IN (" +
                    "   SELECT mediaType, sha256 " +
                    "   FROM media " +
                    "   WHERE sha256 IS NOT NULL " +
                    "   AND sha256 != '' " +
                    "   GROUP BY mediaType, sha256 " +
                    "   HAVING COUNT(*) > 1" +
                    ") " +
                    "ORDER BY mediaType, sha256, relativePath, name, uri"
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
    int getDuplicateGroupCount(String mediaType);

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
    int getDuplicateItemCount(String mediaType);

    @Query(
            "SELECT * FROM media " +
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
                    ") " +
                    "ORDER BY sha256, relativePath, name, uri"
    )
    List<MediaEntity> getExactDuplicateItems(String mediaType);

    @Query(
            "DELETE FROM media " +
                    "WHERE uri IN (:uris)"
    )
    void deleteByUris(List<String> uris);

    @Query(
            "UPDATE media " +
                    "SET quickFingerprint = :fingerprint " +
                    "WHERE uri = :uri"
    )
    void updateQuickFingerprint(String uri, String fingerprint);

    @Query(
            "UPDATE media " +
                    "SET sha256 = :sha256 " +
                    "WHERE uri = :uri"
    )
    void updateSha256(String uri, String sha256);

    @Query(
            "DELETE FROM media " +
                    "WHERE uri = :uri"
    )
    void deleteByUri(String uri);

    // ---------------------------------------------------------
    // VIDEO FINGERPRINT METHODS
    // ---------------------------------------------------------

    @Query(
            "SELECT * FROM media " +
                    "WHERE mediaType = 'VIDEO'"
    )
    List<MediaEntity> getAllVideos();

    @Query(
            "UPDATE media " +
                    "SET videoFingerprint = :fingerprint " +
                    "WHERE uri = :uri"
    )
    void updateVideoFingerprint(
            String uri,
            String fingerprint
    );

    // ---------------------------------------------------------
    // SIMILAR VIDEO METHODS
    // ---------------------------------------------------------

    @Query(
            "SELECT * FROM media " +
                    "WHERE mediaType = 'VIDEO' " +
                    "AND videoFingerprint IS NOT NULL " +
                    "AND videoFingerprint != '' " +
                    "ORDER BY uri"
    )
    List<MediaEntity> getVideosWithFingerprints();
}
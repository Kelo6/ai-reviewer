package com.ai.reviewer.backend.infra.jpa.mapper;

import com.ai.reviewer.backend.infra.jpa.entity.ArtifactEntity;
import com.ai.reviewer.shared.model.ReviewRun;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between ArtifactEntity and ReviewRun.Artifacts record.
 * 
 * <p>Handles the conversion logic between JPA entities and domain records
 * for artifact file paths and metadata.
 */
@Component
public class ArtifactMapper {

    /**
     * Convert ArtifactEntity to ReviewRun.Artifacts record.
     *
     * @param entity the entity to convert
     * @return converted artifacts record
     */
    public ReviewRun.Artifacts toRecord(ArtifactEntity entity) {
        if (entity == null) {
            return new ReviewRun.Artifacts(null, null, null, null);
        }

        return new ReviewRun.Artifacts(
                entity.getSarifPath(),
                entity.getReportMdPath(),
                entity.getReportHtmlPath(),
                entity.getReportPdfPath()
        );
    }

    /**
     * Convert ReviewRun.Artifacts record to ArtifactEntity.
     *
     * @param artifacts the artifacts record to convert
     * @return converted entity
     */
    public ArtifactEntity toEntity(ReviewRun.Artifacts artifacts) {
        if (artifacts == null) {
            return new ArtifactEntity();
        }

        ArtifactEntity entity = new ArtifactEntity();
        entity.setSarifPath(artifacts.sarifPath());
        entity.setReportMdPath(artifacts.reportMdPath());
        entity.setReportHtmlPath(artifacts.reportHtmlPath());
        entity.setReportPdfPath(artifacts.reportPdfPath());
        
        return entity;
    }

    /**
     * Update an existing entity with data from an artifacts record.
     *
     * @param entity the entity to update
     * @param artifacts the artifacts record with new data
     */
    public void updateEntity(ArtifactEntity entity, ReviewRun.Artifacts artifacts) {
        if (entity == null || artifacts == null) {
            return;
        }

        entity.setSarifPath(artifacts.sarifPath());
        entity.setReportMdPath(artifacts.reportMdPath());
        entity.setReportHtmlPath(artifacts.reportHtmlPath());
        entity.setReportPdfPath(artifacts.reportPdfPath());
    }

    /**
     * Check if the entity has any artifact paths set.
     *
     * @param entity the entity to check
     * @return true if at least one path is set
     */
    public boolean hasArtifacts(ArtifactEntity entity) {
        if (entity == null) {
            return false;
        }

        return entity.getSarifPath() != null ||
               entity.getReportMdPath() != null ||
               entity.getReportHtmlPath() != null ||
               entity.getReportPdfPath() != null;
    }

    /**
     * Check if the artifacts record has any paths set.
     *
     * @param artifacts the artifacts record to check
     * @return true if at least one path is set
     */
    public boolean hasArtifacts(ReviewRun.Artifacts artifacts) {
        if (artifacts == null) {
            return false;
        }

        return artifacts.sarifPath() != null ||
               artifacts.reportMdPath() != null ||
               artifacts.reportHtmlPath() != null ||
               artifacts.reportPdfPath() != null;
    }

    /**
     * Count the number of artifact paths that are set.
     *
     * @param entity the entity to check
     * @return count of non-null paths
     */
    public int countArtifacts(ArtifactEntity entity) {
        if (entity == null) {
            return 0;
        }

        int count = 0;
        if (entity.getSarifPath() != null) count++;
        if (entity.getReportMdPath() != null) count++;
        if (entity.getReportHtmlPath() != null) count++;
        if (entity.getReportPdfPath() != null) count++;
        
        return count;
    }

    /**
     * Check if all expected artifact types are present.
     *
     * @param entity the entity to check
     * @return true if all four artifact types are present
     */
    public boolean isComplete(ArtifactEntity entity) {
        if (entity == null) {
            return false;
        }

        return entity.getSarifPath() != null &&
               entity.getReportMdPath() != null &&
               entity.getReportHtmlPath() != null &&
               entity.getReportPdfPath() != null;
    }

    /**
     * Create a summary of available artifacts.
     *
     * @param entity the entity to summarize
     * @return artifact summary
     */
    public ArtifactSummary toSummaryRecord(ArtifactEntity entity) {
        if (entity == null) {
            return new ArtifactSummary(false, false, false, false, 0, 0L);
        }

        return new ArtifactSummary(
                entity.getSarifPath() != null,
                entity.getReportMdPath() != null,
                entity.getReportHtmlPath() != null,
                entity.getReportPdfPath() != null,
                countArtifacts(entity),
                entity.getFileSizeBytes() != null ? entity.getFileSizeBytes() : 0L
        );
    }

    /**
     * Simple summary record for artifact information.
     */
    public record ArtifactSummary(
            boolean hasSarif,
            boolean hasMarkdown,
            boolean hasHtml,
            boolean hasPdf,
            int artifactCount,
            long totalSizeBytes
    ) {}
}

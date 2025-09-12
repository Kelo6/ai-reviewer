package com.ai.reviewer.backend.infra.jpa.mapper;

import com.ai.reviewer.backend.infra.jpa.entity.FindingEntity;
import com.ai.reviewer.shared.model.Finding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between FindingEntity and Finding record.
 * 
 * <p>Handles the conversion logic between JPA entities and domain records,
 * including JSON serialization/deserialization for the sources field.
 */
@Component
public class FindingMapper {

    private final ObjectMapper objectMapper;

    @Autowired
    public FindingMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Convert FindingEntity to Finding record.
     *
     * @param entity the entity to convert
     * @return converted record
     */
    public Finding toRecord(FindingEntity entity) {
        if (entity == null) {
            return null;
        }

        return new Finding(
                entity.getId(),
                entity.getFile(),
                entity.getStartLine(),
                entity.getEndLine(),
                entity.getSeverity(),
                entity.getDimension(),
                entity.getTitle(),
                entity.getEvidence(),
                entity.getSuggestion(),
                entity.getPatch(),
                parseSources(entity.getSourcesJson()),
                entity.getConfidence().doubleValue()
        );
    }

    /**
     * Convert Finding record to FindingEntity.
     *
     * @param record the record to convert
     * @return converted entity
     */
    public FindingEntity toEntity(Finding record) {
        if (record == null) {
            return null;
        }

        FindingEntity entity = new FindingEntity();
        entity.setId(record.id());
        entity.setFile(record.file());
        entity.setStartLine(record.startLine());
        entity.setEndLine(record.endLine());
        entity.setSeverity(record.severity());
        entity.setDimension(record.dimension());
        entity.setTitle(record.title());
        entity.setEvidence(record.evidence());
        entity.setSuggestion(record.suggestion());
        entity.setPatch(record.patch());
        entity.setSourcesJson(serializeSources(record.sources()));
        entity.setConfidence(BigDecimal.valueOf(record.confidence()));

        return entity;
    }

    /**
     * Convert list of FindingEntity to list of Finding records.
     *
     * @param entities the entities to convert
     * @return converted records
     */
    public List<Finding> toRecords(List<FindingEntity> entities) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    /**
     * Convert list of Finding records to list of FindingEntity.
     *
     * @param records the records to convert
     * @return converted entities
     */
    public List<FindingEntity> toEntities(List<Finding> records) {
        if (records == null) {
            return List.of();
        }

        return records.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * Update an existing entity with data from a record.
     *
     * @param entity the entity to update
     * @param record the record with new data
     */
    public void updateEntity(FindingEntity entity, Finding record) {
        if (entity == null || record == null) {
            return;
        }

        // Update mutable fields
        entity.setTitle(record.title());
        entity.setEvidence(record.evidence());
        entity.setSuggestion(record.suggestion());
        entity.setPatch(record.patch());
        entity.setSourcesJson(serializeSources(record.sources()));
        entity.setConfidence(BigDecimal.valueOf(record.confidence()));
    }

    // JSON serialization helpers

    private List<String> parseSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isEmpty()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(sourcesJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse sources JSON: " + sourcesJson, e);
        }
    }

    private String serializeSources(List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return "[]";
        }

        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize sources", e);
        }
    }

    /**
     * Create a Finding summary record with essential information only.
     *
     * @param entity the entity to convert
     * @return summary record
     */
    public FindingSummary toSummaryRecord(FindingEntity entity) {
        if (entity == null) {
            return null;
        }

        return new FindingSummary(
                entity.getId(),
                entity.getFile(),
                entity.getStartLine(),
                entity.getEndLine(),
                entity.getSeverity(),
                entity.getDimension(),
                entity.getTitle(),
                entity.getConfidence().doubleValue(),
                entity.getPatch() != null
        );
    }

    /**
     * Simple summary record for list views and API responses.
     */
    public record FindingSummary(
            String id,
            String file,
            int startLine,
            int endLine,
            com.ai.reviewer.shared.enums.Severity severity,
            com.ai.reviewer.shared.enums.Dimension dimension,
            String title,
            double confidence,
            boolean hasPatch
    ) {}
}

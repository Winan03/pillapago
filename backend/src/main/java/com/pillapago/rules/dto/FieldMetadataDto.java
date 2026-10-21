package com.pillapago.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMetadataDto {
    private String field;
    private String label;
    private String type; // NUMERIC, STRING, BOOLEAN
    private String category; // TRANSACTION, VELOCITY
    private List<String> allowedOperators;
    private String placeholder;
}

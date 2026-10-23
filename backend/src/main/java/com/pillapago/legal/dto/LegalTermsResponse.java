package com.pillapago.legal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO que expone las cláusulas y permisos legales obligatorios del Sistema Antifraude Pillapago.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalTermsResponse {

    private String version;
    private String effectiveDate;
    private String title;
    private String summary;
    private List<LegalClauseDto> clauses;
    private PciDssStatementDto pciDssCompliance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalClauseDto {
        private String key;
        private String title;
        private String description;
        private boolean mandatory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PciDssStatementDto {
        private boolean panStored;
        private boolean cvvStored;
        private String encryptionStandard;
        private String cardDataHandling;
    }
}

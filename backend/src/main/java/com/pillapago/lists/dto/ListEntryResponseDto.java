package com.pillapago.lists.dto;

import com.pillapago.lists.domain.ListEntryType;
import com.pillapago.lists.domain.ListType;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de respuesta para una entrada de lista de control de acceso (HU-13).
 * Muestra el valor enmascarado para resguardar la privacidad de datos personales (RNF-15).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListEntryResponseDto {

    private UUID id;
    private ListType listType;
    private ListEntryType entryType;
    private String valueMasked;
    private String reason;
    private OffsetDateTime expiresAt;
    private boolean active;
    private UUID createdById;
    private String createdByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

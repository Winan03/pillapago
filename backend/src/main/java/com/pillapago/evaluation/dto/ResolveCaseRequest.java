package com.pillapago.evaluation.dto;

import com.pillapago.evaluation.domain.CaseResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de solicitud para resolver un caso de revisión manual (HU-09).
 * Exige comentario obligatorio y decisión de aprobación o rechazo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveCaseRequest {

    @NotNull(message = "La resolución es obligatoria (APPROVED o REJECTED)")
    private CaseResolution resolution;

    @NotBlank(message = "El comentario o justificación es obligatorio para resolver el caso")
    @Size(min = 5, max = 2000, message = "El comentario debe contener entre 5 y 2000 caracteres")
    private String comment;
}

package com.pillapago.lists.dto;

import com.pillapago.lists.domain.ListEntryType;
import com.pillapago.lists.domain.ListType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Payload para registrar una nueva entrada en listas de bloqueo o confianza (HU-13).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateListEntryRequest {

    @NotNull(message = "El tipo de lista es obligatorio (BLOCK o ALLOW)")
    private ListType listType;

    @NotNull(message = "El tipo de entrada es obligatorio (DEVICE, IP, EMAIL, CARD_FINGERPRINT)")
    private ListEntryType entryType;

    @NotBlank(message = "El valor a incorporar en la lista es obligatorio")
    @Size(max = 255, message = "El valor no puede superar los 255 caracteres")
    private String value;

    @NotBlank(message = "El motivo de inclusión es obligatorio")
    @Size(min = 5, max = 500, message = "El motivo debe tener entre 5 y 500 caracteres")
    private String reason;

    @Future(message = "La fecha de vencimiento debe ser posterior a la fecha actual")
    private OffsetDateTime expiresAt;
}

package com.pillapago.evaluation.event;

import com.pillapago.evaluation.dto.EvaluateTransactionRequest;
import com.pillapago.evaluation.dto.EvaluateTransactionResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento emitido al finalizar la evaluación en el Hot Path para persistencia asíncrona (HU-06c).
 */
@Getter
@Builder
public class TransactionEvaluatedEvent {

    private final UUID merchantId;
    private final EvaluateTransactionRequest request;
    private final EvaluateTransactionResponse response;
    private final OffsetDateTime timestamp;
}

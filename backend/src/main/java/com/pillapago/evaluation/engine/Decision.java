package com.pillapago.evaluation.engine;

/**
 * Decisión antifraude resultante de la evaluación de una transacción.
 */
public enum Decision {
    /**
     * Transacción de bajo riesgo aprobada automáticamente para cobro.
     */
    APPROVE,

    /**
     * Transacción con riesgo intermedio; derivada a la cola de revisión manual.
     */
    REVIEW,

    /**
     * Transacción de alto riesgo rechazada inmediatamente.
     */
    REJECT
}

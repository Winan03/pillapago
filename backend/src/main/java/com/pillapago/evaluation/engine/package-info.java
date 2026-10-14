/**
 * Motor de decisión — Java puro, sin dependencias de Spring ni JPA.
 * Evaluable con JUnit sin levantar el contexto de Spring.
 *
 * Clases principales (se implementan en la Fase 1 · HU-06a):
 *   Rule            → Condición + puntos de riesgo
 *   Condition       → Campo + operador + valor de comparación
 *   RuleEvaluator   → Evalúa una lista de Rule contra una TransactionInput
 *   ScoreCalculator → Suma los puntos y mapea el score a una Decision
 */
package com.pillapago.evaluation.engine;

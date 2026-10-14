# ADR-01 · Persistencia asíncrona de decisiones

**Estado:** Aceptado  
**Fecha:** 2026-10-08  

## Contexto
El endpoint de evaluación debe responder en decenas de ms (RNF-01). Guardar la decisión en PostgreSQL en el mismo hilo de la petición añadiría latencia variable y acoplaría el hot path a la base de datos.

## Decisión
Se responde al comercio **antes** de persistir la decisión. Después de responder se publica un evento en la cola (`ColaPort`) que un consumidor procesa para guardar en PostgreSQL.

## Consecuencias
- **Positivo:** latencia de respuesta independiente de la escritura en BD.
- **Riesgo:** si el proceso cae entre la respuesta y la publicación del evento, se pierde el registro de auditoría.
- **Mitigación MVP:** la respuesta queda en Redis con TTL de 24 h; un proceso de conciliación periódico detecta huecos comparando Redis con PostgreSQL.
- **Fase 2:** se implementa el patrón *outbox* (ADR-08) que elimina este hueco.

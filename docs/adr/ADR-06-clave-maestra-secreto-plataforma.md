# ADR-06 · Sin servicios AWS de pago en la nube

**Estado:** Aceptado  
**Fecha:** 2026-10-08  

## Contexto
El proyecto es de portafolio. No se pueden usar servicios AWS reales con costo. Sin embargo, el diseño debe demostrar conocimiento de S3, SQS y KMS.

## Decisión
- **Local:** los adaptadores usan el SDK de AWS contra **Floci** (emulador ligero de AWS).
- **Nube gratuita:** la clave maestra se guarda como secreto de Render; `ColaPort` usa el patrón *outbox* en Postgres; `CorreoPort` usa Resend o Brevo.
- Un perfil `aws` con servicios reales queda documentado pero no desplegado.

## Consecuencias
- **Positivo:** costo cero; el código de negocio no cambia (depende de puertos, no del SDK).
- **Compromiso:** la clave maestra como secreto de plataforma (Render) es más débil que KMS real. Solo se usa con datos sintéticos.
- **Mitigación:** cifrado de campo con Tink, clave solo en variable secreta, y perfil `aws` documentado para producción real.

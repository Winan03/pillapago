package com.pillapago.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Constructor de Datos Asociados (AEAD Associated Data - AAD) para Google Tink AES-256-GCM.
 * Vincula criptográficamente el texto cifrado con el contexto exacto donde reside:
 *   AAD = merchant_id : table_name : column_name : row_id
 *
 * Si un atacante copia un dato cifrado a otra fila, otra columna o a otro comercio,
 * la verificación de autenticación GCM fallará de inmediato durante el descifrado.
 */
public final class AssociatedDataBuilder {

    private AssociatedDataBuilder() {
    }

    public static byte[] build(UUID merchantId, String table, String column, Object rowId) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo para construir AAD");
        }
        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("table no puede ser nula o vacía");
        }
        if (column == null || column.trim().isEmpty()) {
            throw new IllegalArgumentException("column no puede ser nula o vacía");
        }
        if (rowId == null) {
            throw new IllegalArgumentException("rowId no puede ser nulo");
        }

        String aadString = merchantId + ":" + table.trim().toLowerCase() + ":" + column.trim().toLowerCase() + ":" + rowId;
        return aadString.getBytes(StandardCharsets.UTF_8);
    }
}
